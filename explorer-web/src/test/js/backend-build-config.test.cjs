const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");

const serverRoot = path.resolve(__dirname, "../../../../");

function readServerPom() {
  return fs.readFileSync(path.join(serverRoot, "pom.xml"), "utf8");
}

function readModulePom(moduleName) {
  return fs.readFileSync(path.join(serverRoot, moduleName, "pom.xml"), "utf8");
}

function compareVersions(left, right) {
  const leftParts = left.split(".").map(Number);
  const rightParts = right.split(".").map(Number);
  const maxLength = Math.max(leftParts.length, rightParts.length);

  for (let index = 0; index < maxLength; index += 1) {
    const leftPart = leftParts[index] || 0;
    const rightPart = rightParts[index] || 0;
    if (leftPart !== rightPart) return leftPart - rightPart;
  }

  return 0;
}

test("backend uses a Lombok version compatible with modern IDEA JDKs", () => {
  const pom = readServerPom();
  const version = pom.match(/<lombok>([^<]+)<\/lombok>/)?.[1];

  assert.ok(version, "root pom should declare a managed Lombok version");
  assert.ok(
    compareVersions(version, "1.18.44") >= 0,
    `Lombok ${version} can fail in IDEA with JDK 24 javac TypeTag.UNKNOWN; use 1.18.44 or newer`
  );
});

test("spring-boot run is skipped for aggregator modules and enabled only for explorer-web", () => {
  const rootPom = readServerPom();
  const webPom = readModulePom("explorer-web");

  assert.match(rootPom, /<spring-boot\.run\.skip>true<\/spring-boot\.run\.skip>/);
  assert.match(rootPom, /<artifactId>jacoco-maven-plugin<\/artifactId>/);
  assert.match(rootPom, /<destFile>\$\{java\.io\.tmpdir\}\/local-explorer-jacoco-\$\{project\.artifactId\}\.exec<\/destFile>/);
  assert.match(rootPom, /<dataFile>\$\{java\.io\.tmpdir\}\/local-explorer-jacoco-\$\{project\.artifactId\}\.exec<\/dataFile>/);
  assert.match(webPom, /<spring-boot\.run\.skip>false<\/spring-boot\.run\.skip>/);
  assert.match(webPom, /<mainClass>com\.localexplorer\.LocalExplorerApplication<\/mainClass>/);
});

test("maven wrapper download works on clean Windows PowerShell", () => {
  const wrapper = fs.readFileSync(path.join(serverRoot, "mvnw.cmd"), "utf8");

  assert.match(wrapper, /SecurityProtocolType\]::Tls12/);
  assert.match(wrapper, /repo\.maven\.apache\.org\/maven2\/org\/apache\/maven\/apache-maven/);
  assert.match(wrapper, /\$zip=\[IO\.Path\]::GetFullPath\(\$env:MAVEN_ZIP\)/);
  assert.match(wrapper, /\$dest=\[IO\.Path\]::GetFullPath\(\$env:WRAPPER_DIR\)/);
  assert.doesNotMatch(wrapper, /!\(Test-Path/);
  assert.match(wrapper, /\$temp=\$zip\+'\.download'/);
  assert.match(wrapper, /ZipFile\]::OpenRead/);
  assert.match(wrapper, /Remove-Item -LiteralPath \$zip -Force/);
  assert.match(wrapper, /Invoke-WebRequest -Uri \$url -OutFile \$temp -UseBasicParsing/);
  assert.match(wrapper, /Move-Item -LiteralPath \$temp -Destination \$zip -Force/);
  assert.match(wrapper, /Get-Item -LiteralPath \$zip -ErrorAction Stop/);
  assert.match(wrapper, /\$archive\.Length -lt 1000000/);
  assert.match(wrapper, /Expand-Archive -LiteralPath \$archive\.FullName/);
});

test("git repository root contains the files needed for handoff and local run", () => {
  for (const requiredPath of [
    "README.md",
    "run.cmd",
    "docker-compose.yml",
    ".env.example",
    path.join("docs", "local-explorer-init.sql"),
    path.join("docs", "local-explorer-migrate.sql"),
    path.join("scripts", "run-demo.ps1"),
    path.join("scripts", "run-frontend.ps1")
  ]) {
    assert.equal(
      fs.existsSync(path.join(serverRoot, requiredPath)),
      true,
      `${requiredPath} should live under the git repository root`
    );
  }
});

test("category creation defaults optional sort to avoid insert failures from simple admin forms", () => {
  const serviceSource = fs.readFileSync(
    path.join(serverRoot, "explorer-web", "src", "main", "java", "com", "localexplorer", "service", "impl", "CategoryServiceImpl.java"),
    "utf8"
  );

  assert.match(serviceSource, /if \(category\.getSort\(\) == null\)/);
  assert.match(serviceSource, /category\.setSort\(0\)/);
});

test("spring static resource mapping serves built frontend assets", () => {
  const webConfig = fs.readFileSync(
    path.join(serverRoot, "explorer-web", "src", "main", "java", "com", "localexplorer", "config", "WebMvcConfiguration.java"),
    "utf8"
  );
  const consoleHtml = fs.readFileSync(path.join(serverRoot, "explorer-web", "src", "main", "resources", "static", "console", "index.html"), "utf8");
  const clientHtml = fs.readFileSync(path.join(serverRoot, "explorer-web", "src", "main", "resources", "static", "client", "index.html"), "utf8");

  assert.match(consoleHtml, /\.\.\/assets\/app\//);
  assert.match(clientHtml, /\.\.\/assets\/app\//);
  assert.match(consoleHtml, /\.\.\/assets\/demo-data\.js/);
  assert.match(clientHtml, /\.\.\/assets\/demo-data\.js/);
  assert.match(webConfig, /addResourceHandler\("\/assets\/\*\*"\)/);
  assert.match(webConfig, /addResourceLocations\("classpath:\/static\/assets\/"\)/);
});

test("spring root routes redirect to usable frontend entry points", () => {
  const webConfig = fs.readFileSync(
    path.join(serverRoot, "explorer-web", "src", "main", "java", "com", "localexplorer", "config", "WebMvcConfiguration.java"),
    "utf8"
  );

  assert.match(webConfig, /addViewControllers\(ViewControllerRegistry registry\)/);
  assert.match(webConfig, /addRedirectViewController\("\/", "\/console\/login\.html"\)/);
  assert.match(webConfig, /addRedirectViewController\("\/console", "\/console\/login\.html"\)/);
  assert.match(webConfig, /addRedirectViewController\("\/client", "\/client\/login\.html"\)/);
});

test("optional Redis fails fast instead of blocking each request for seconds", () => {
  const application = fs.readFileSync(
    path.join(serverRoot, "explorer-web", "src", "main", "resources", "application.yml"),
    "utf8"
  );

  assert.match(application, /redis:\s+[\s\S]*?connect-timeout:\s*200ms/);
  assert.match(application, /redis:\s+[\s\S]*?timeout:\s*200ms/);
});

test("actuator health exposes database state and Redis fallback degradation", () => {
  const webPom = readModulePom("explorer-web");
  const application = fs.readFileSync(
    path.join(serverRoot, "explorer-web", "src", "main", "resources", "application.yml"),
    "utf8"
  );
  const indicator = fs.readFileSync(
    path.join(serverRoot, "explorer-web", "src", "main", "java", "com", "localexplorer", "health", "RedisFallbackHealthIndicator.java"),
    "utf8"
  );

  assert.match(webPom, /spring-boot-starter-actuator/);
  assert.match(application, /exposure:\s+[\s\S]*?include:\s*health/);
  assert.match(application, /show-components:\s*always/);
  assert.match(application, /redis:\s+[\s\S]*?enabled:\s*false/);
  assert.match(application, /degraded:\s*200/);
  assert.match(indicator, /implements HealthIndicator/);
  assert.match(indicator, /status\("DEGRADED"\)/);
  assert.match(indicator, /l1-mysql-fallback/);
});

test("web configuration registers the admin authorization boundary after JWT authentication", () => {
  const webConfig = fs.readFileSync(
    path.join(serverRoot, "explorer-web", "src", "main", "java", "com", "localexplorer", "config", "WebMvcConfiguration.java"),
    "utf8"
  );

  assert.match(webConfig, /AdminAuthorizationInterceptor/);
  assert.match(webConfig, /addInterceptor\(jwtTokenAdminInterceptor\)[\s\S]*addInterceptor\(adminAuthorizationInterceptor\)/);
});

test("Druid disables reflective MySQL ping before the datasource is created", () => {
  const application = fs.readFileSync(
    path.join(serverRoot, "explorer-web", "src", "main", "resources", "application.yml"),
    "utf8"
  );
  const applicationSource = fs.readFileSync(
    path.join(serverRoot, "explorer-web", "src", "main", "java", "com", "localexplorer", "LocalExplorerApplication.java"),
    "utf8"
  );

  assert.match(application, /validation-query:\s*SELECT 1/);
  assert.match(application, /test-while-idle:\s*true/);
  assert.match(applicationSource, /System\.setProperty\("druid\.mysql\.usePingMethod", "false"\)/);
  assert.doesNotMatch(application, /connection-properties:\s*druid\.mysql\.usePingMethod=false/);
});

test("backend does not keep unused external upload and http client leftovers", () => {
  const rootPom = readServerPom();
  const commonPom = readModulePom("explorer-common");
  const webPom = readModulePom("explorer-web");
  const application = fs.readFileSync(
    path.join(serverRoot, "explorer-web", "src", "main", "resources", "application.yml"),
    "utf8"
  );
  const applicationDev = fs.readFileSync(
    path.join(serverRoot, "explorer-web", "src", "main", "resources", "application-dev.yml"),
    "utf8"
  );
  const applicationDevExample = fs.readFileSync(
    path.join(serverRoot, "explorer-web", "src", "main", "resources", "application-dev.example.yml"),
    "utf8"
  );

  for (const filePath of [
    path.join("explorer-common", "src", "main", "java", "com", "localexplorer", "properties", "AliOssProperties.java"),
    path.join("explorer-common", "src", "main", "java", "com", "localexplorer", "utils", "AliOssUtil.java"),
    path.join("explorer-common", "src", "main", "java", "com", "localexplorer", "utils", "HttpClientUtil.java"),
    path.join("explorer-web", "src", "main", "java", "com", "localexplorer", "config", "OssConfiguration.java"),
    path.join("explorer-web", "src", "main", "java", "com", "localexplorer", "controller", "admin", "CommonController.java")
  ]) {
    assert.equal(fs.existsSync(path.join(serverRoot, filePath)), false, `${filePath} should be removed`);
  }

  for (const source of [rootPom, commonPom, webPom, application, applicationDev, applicationDevExample]) {
    assert.doesNotMatch(source, /alioss|aliyun|OSS_|oss-cn|fastjson|HttpClient/i);
  }
});

test("database migration script patches old schemas without dropping data", () => {
  const migration = fs.readFileSync(path.join(serverRoot, "docs", "local-explorer-migrate.sql"), "utf8");

  assert.match(migration, /information_schema\.COLUMNS/);
  assert.match(migration, /TABLE_NAME = 'explore_item'/);
  assert.match(migration, /COLUMN_NAME = 'duration_minutes'/);
  assert.match(migration, /COLUMN_NAME = 'booked'/);
  assert.match(migration, /TABLE_NAME = 'explore_package'/);
  assert.match(migration, /UPDATE explore_item SET capacity = 24/);
  assert.match(migration, /UPDATE explore_package SET capacity = 20/);
  assert.match(migration, /TABLE_NAME = 'user'/);
  assert.match(migration, /COLUMN_NAME = 'status'/);
  assert.match(migration, /ALTER TABLE user ADD COLUMN status int\(11\) NOT NULL DEFAULT 1/);
  assert.doesNotMatch(migration, /DROP\s+(DATABASE|TABLE)/i);
});

test("handoff ignores generated caches while keeping tests trackable", () => {
  const gitignore = fs.readFileSync(path.join(serverRoot, ".gitignore"), "utf8");
  const readme = fs.readFileSync(path.join(serverRoot, "README.md"), "utf8");

  assert.match(gitignore, /\.m2\//);
  assert.match(gitignore, /\.mvn\/wrapper\/apache-maven-\*\//);
  assert.match(gitignore, /\.mvn\/wrapper\/apache-maven-\*-bin\.zip/);
  assert.match(gitignore, /\.superpowers\//);
  assert.match(gitignore, /\*\*\/node_modules\//);
  assert.match(gitignore, /\*\.log/);
  assert.doesNotMatch(gitignore, /\*Test\.java/);
  assert.doesNotMatch(gitignore, /\*\*\/test\//);

  assert.match(readme, /### 发给别人前清理/);
  assert.match(readme, /frontend\/node_modules\//);
  assert.match(readme, /package-lock\.json/);
});

test("ci workflow runs backend and node contract tests", () => {
  const workflowPath = path.join(serverRoot, ".github", "workflows", "ci.yml");
  assert.equal(fs.existsSync(workflowPath), true, "CI workflow should exist");

  const workflow = fs.readFileSync(workflowPath, "utf8");
  assert.match(workflow, /actions\/checkout@v4/);
  assert.match(workflow, /actions\/setup-java@v4/);
  assert.match(workflow, /distribution:\s*temurin/);
  assert.match(workflow, /java-version:\s*8/);
  assert.match(workflow, /actions\/setup-node@v4/);
  assert.match(workflow, /node-version:\s*20/);
  assert.match(workflow, /\.\\\/mvnw\.cmd test|\.\/mvnw test/);
  assert.match(workflow, /node --test explorer-web\/src\/test\/js\/\*\.test\.cjs/);
});

test("ci uploads asynchronous export evidence and runs browser closure", () => {
  const workflow = fs.readFileSync(path.join(serverRoot, ".github", "workflows", "ci.yml"), "utf8");

  assert.match(workflow, /npm run smoke:export/);
  assert.match(workflow, /npm run smoke:export:demo/);
  assert.match(workflow, /export-performance\.json/);
  assert.match(workflow, /real-mysql-smoke\.json/);
  assert.match(workflow, /export-100000\.csv/);
  assert.match(workflow, /export-100000\.xlsx/);
});

test("ci verifies the JDK8 frontend build and Playwright notification flow", () => {
  const workflow = fs.readFileSync(path.join(serverRoot, ".github", "workflows", "ci.yml"), "utf8");

  assert.match(workflow, /java-version:\s*8/);
  assert.match(workflow, /working-directory:\s*explorer-web\/frontend/);
  assert.match(workflow, /npm ci/);
  assert.match(workflow, /npm run build/);
  assert.match(workflow, /playwright install --with-deps chromium/);
  assert.match(workflow, /npm run smoke:notification/);
  assert.match(workflow, /notification-desktop\.png/);
  assert.match(workflow, /notification-mobile\.png/);
});

test("integration-test profile runs real MySQL tests without changing the default test command", () => {
  const webPom = readModulePom("explorer-web");

  assert.match(webPom, /<id>integration-test<\/id>/);
  assert.match(webPom, /org\.testcontainers/);
  assert.match(webPom, /<artifactId>mysql<\/artifactId>/);
  assert.match(webPom, /<artifactId>maven-failsafe-plugin<\/artifactId>/);
  assert.match(webPom, /src\/integration-test\/java/);
  assert.match(webPom, /local-explorer-init\.sql/);
  assert.match(webPom, /jacoco-it/);
});

test("profiles expose Prometheus only in development and test environments", () => {
  const webPom = readModulePom("explorer-web");
  const resources = path.join(serverRoot, "explorer-web", "src", "main", "resources");
  const application = fs.readFileSync(path.join(resources, "application.yml"), "utf8");
  const development = fs.readFileSync(path.join(resources, "application-dev.yml"), "utf8");
  const testing = fs.readFileSync(path.join(resources, "application-test.yml"), "utf8");
  const production = fs.readFileSync(path.join(resources, "application-prod.yml"), "utf8");

  assert.match(webPom, /micrometer-registry-prometheus/);
  assert.match(application, /active:\s*\$\{SPRING_PROFILES_ACTIVE:dev\}/);
  assert.doesNotMatch(application, /include:\s*\*/);
  assert.match(development, /include:\s*health,prometheus/);
  assert.match(testing, /include:\s*health,prometheus/);
  assert.match(production, /include:\s*health/);
  assert.doesNotMatch(production, /prometheus|include:\s*\*/);
  assert.match(production, /cookie-secure:\s*\$\{AUTH_COOKIE_SECURE:true\}/);
});

test("ci has an isolated Testcontainers MySQL and Redis job", () => {
  const workflow = fs.readFileSync(path.join(serverRoot, ".github", "workflows", "ci.yml"), "utf8");

  assert.match(workflow, /integration-test:/);
  assert.match(workflow, /\.\/mvnw -Pintegration-test verify/);
  assert.match(workflow, /jacoco-it/);
  assert.match(workflow, /surefire-reports/);
  assert.match(workflow, /failsafe-reports/);
  assert.match(workflow, /redis:7/);
  assert.match(workflow, /HotCacheMySqlRedisIT|MySQL and Redis integration tests/);
});

test("ci captures real cache hot-path performance evidence", () => {
  const workflow = fs.readFileSync(path.join(serverRoot, ".github", "workflows", "ci.yml"), "utf8");
  const smoke = fs.readFileSync(path.join(serverRoot, "scripts", "smoke-cache-performance.cjs"), "utf8");

  assert.match(workflow, /node scripts\/smoke-cache-performance\.cjs/);
  assert.match(workflow, /CACHE_SMOKE_REQUESTS:\s*200/);
  assert.match(workflow, /cache-performance-report\.json/);
  assert.match(smoke, /Math\.max\(200/);
  assert.match(smoke, /p50Ms/);
  assert.match(smoke, /p95Ms/);
  assert.match(smoke, /p99Ms/);
  assert.match(smoke, /throughputRps/);
  assert.match(smoke, /databaseLoads/);
  assert.match(smoke, /hitRate/);
});

test("ci runs the real order reliability smoke against a started backend", () => {
  const workflow = fs.readFileSync(path.join(serverRoot, ".github", "workflows", "ci.yml"), "utf8");

  assert.match(workflow, /services:[\s\S]*mysql:[\s\S]*redis:/);
  assert.match(workflow, /local-explorer-init\.sql/);
  assert.match(workflow, /ORDER_PENDING_TIMEOUT_MINUTES:\s*1/);
  assert.match(workflow, /ORDER_EXPIRATION_DELAY_MS:\s*1000/);
  assert.match(workflow, /OUTBOX_DELAY_MS:\s*500/);
  assert.match(workflow, /java -jar explorer-web\/target\/local-explorer-web-1\.0-SNAPSHOT\.jar/);
  assert.match(workflow, /node scripts\/smoke-order-reliability\.cjs/);
  assert.match(workflow, /\/actuator\/health/);
  assert.match(workflow, /\/v2\/api-docs/);
  assert.match(workflow, /\/client\/index\.html/);
  assert.match(workflow, /Stop reliability backend/);
});

test("ci runs real authentication API and browser smokes with evidence", () => {
  const workflow = fs.readFileSync(path.join(serverRoot, ".github", "workflows", "ci.yml"), "utf8");

  assert.match(workflow, /node scripts\/smoke-auth-session\.cjs/);
  assert.match(workflow, /npm run smoke:auth/);
  assert.match(workflow, /auth-admin-desktop\.png/);
  assert.match(workflow, /auth-user-mobile\.png/);
  assert.match(workflow, /AUTH_REPLAY_GRACE_MILLIS:\s*\d+/);
});

test("runtime logging does not expose SQL parameter values", () => {
  const application = fs.readFileSync(
    path.join(serverRoot, "explorer-web", "src", "main", "resources", "application.yml"),
    "utf8"
  );

  assert.doesNotMatch(application, /mapper:\s*debug/i);
  assert.match(application, /mapper:\s*info/i);
  assert.match(application, /%X\{batchId:-no-batch\}/);
});
