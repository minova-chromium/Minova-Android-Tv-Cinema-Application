"use strict";

// The HTML links are direct, usable downloads even without JavaScript or API access.
(async function refreshKometaDownload() {
  try {
    const response = await fetch(
      "https://api.github.com/repos/minova-chromium/Minova-Kometa-Helper/releases/latest",
      { headers: { accept: "application/vnd.github+json" }, signal: AbortSignal.timeout(6000) }
    );
    if (!response.ok) return;
    const release = await response.json();
    if (release.draft || release.prerelease || !Array.isArray(release.assets)) return;
    const asset = release.assets.find((item) =>
      /^(?:Minova[- ])?Kometa[- ]Helper[- ].*Windows.*\.zip$/i.test(item.name || "")
    );
    if (!asset) return;
    const url = new URL(asset.browser_download_url);
    if (url.origin !== "https://github.com" ||
        !url.pathname.startsWith("/minova-chromium/Minova-Kometa-Helper/releases/download/")) return;
    document.querySelectorAll("[data-kometa-download]").forEach((link) => { link.href = url.href; });
    document.querySelectorAll("[data-kometa-version]").forEach((node) => {
      node.textContent = String(release.tag_name || "Latest release");
    });
    if (Number.isFinite(asset.size) && asset.size > 0) {
      document.querySelectorAll("[data-kometa-size]").forEach((node) => {
        node.textContent = `${(asset.size / 1048576).toFixed(1)} MB`;
      });
    }
  } catch {
    // Keep the verified release asset as the fallback; never redirect to a repository page.
  }
})();
