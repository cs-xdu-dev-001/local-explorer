package com.localexplorer.cache;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public final class CacheInvalidation {
    private final Set<HotCacheDomain> clearedDomains;
    private final List<KeyInvalidation> keys;

    private CacheInvalidation(Set<HotCacheDomain> clearedDomains, List<KeyInvalidation> keys) {
        EnumSet<HotCacheDomain> domains = EnumSet.noneOf(HotCacheDomain.class);
        domains.addAll(clearedDomains);
        this.clearedDomains = Collections.unmodifiableSet(domains);
        this.keys = Collections.unmodifiableList(new ArrayList<>(keys));
    }

    public static Builder builder() {
        return new Builder();
    }

    public Set<HotCacheDomain> getClearedDomains() {
        return clearedDomains;
    }

    public List<KeyInvalidation> getKeys() {
        return keys;
    }

    public static final class Builder {
        private final Set<HotCacheDomain> clearedDomains = EnumSet.noneOf(HotCacheDomain.class);
        private final List<KeyInvalidation> keys = new ArrayList<>();

        public Builder clear(HotCacheDomain domain) {
            clearedDomains.add(domain);
            return this;
        }

        public Builder evict(HotCacheDomain domain, Object businessKey) {
            if (businessKey != null) {
                keys.add(new KeyInvalidation(domain, String.valueOf(businessKey)));
            }
            return this;
        }

        public CacheInvalidation build() {
            return new CacheInvalidation(clearedDomains, keys);
        }
    }

    public static final class KeyInvalidation {
        private final HotCacheDomain domain;
        private final String businessKey;

        private KeyInvalidation(HotCacheDomain domain, String businessKey) {
            this.domain = domain;
            this.businessKey = businessKey;
        }

        public HotCacheDomain getDomain() {
            return domain;
        }

        public String getBusinessKey() {
            return businessKey;
        }
    }
}
