"use strict";

(async function refreshCinemaReleases() {
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

  const applyDesktopRelease = (version, downloadUrl) => {
    if (version) {
      document.querySelectorAll("[data-desktop-version]").forEach((node) => {
        node.textContent = version;
      });
    }
    if (downloadUrl) {
      document.querySelectorAll("[data-desktop-download]").forEach((link) => {
        link.href = downloadUrl;
      });
    }
  };

  applyRelease(config.currentVersion, config.latestApkUrl);
  applyDesktopRelease(config.desktopVersion, config.latestDesktopUrl);

  const refreshAndroid = async () => {
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
  };

  const refreshDesktop = async () => {
    const response = await fetch(config.desktopReleaseApiUrl, {
      headers: { accept: "application/vnd.github+json" }
    });
    if (!response.ok) return;
    const release = await response.json();
    const installer = Array.isArray(release.assets)
      ? release.assets.find((asset) => /Minova-Cinema-Desktop-.*-Setup\.exe$/i.test(asset.name || ""))
      : null;
    applyDesktopRelease(
      String(release.tag_name || "").replace(/^v/i, ""),
      installer?.browser_download_url || config.latestDesktopUrl
    );
  };

  await Promise.allSettled([refreshAndroid(), refreshDesktop()]);
})();
