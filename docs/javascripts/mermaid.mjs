import mermaid from "https://cdn.jsdelivr.net/npm/mermaid@11/dist/mermaid.esm.min.mjs";

mermaid.initialize({
  startOnLoad: false,
  securityLevel: "strict",
  theme: "neutral"
});

document$.subscribe(() => {
  const diagrams = document.querySelectorAll(".mermaid:not([data-processed])");
  if (diagrams.length > 0) {
    mermaid.run({ nodes: diagrams });
  }
});
