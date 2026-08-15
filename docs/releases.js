"use strict";

(async function refreshCinemaRelease() {
  const config = window.MINOVA_CINEMA_CONFIG || {};
  const applyRelease = (version, downloadUrl) => {
    if (version) {
      document.querySelectorAll("[data-release-version]").forEach((node) => {
        node.textContent = version;
      });
      document.querySelectorAll("[data-release-version-input]").forEach((input) => {
        input.value = version;
      });
    }
    if (downloadUrl) {
      document.querySelectorAll("[data-download-link]").forEach((link) => {
        link.href = downloadUrl;
      });
    }
  };

  applyRelease(config.currentVersion, config.latestApkUrl);

  try {
    const response = await fetch(config.latestReleaseApiUrl, {
      headers: { accept: "application/vnd.github+json" }
    });
    if (!response.ok) return;
    const release = await response.json();
    const apk = Array.isArray(release.assets)
      ? release.assets.find((asset) => /Minova-Cinema-.*\.apk$/i.test(asset.name || ""))
      : null;
    applyRelease(
      String(release.tag_name || "").replace(/^v/i, ""),
      apk?.browser_download_url || config.latestApkUrl
    );
  } catch {
    // Static fallback remains a working direct download.
  }
})();
