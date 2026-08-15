"use strict";

(function initializeCinemaFeedback() {
  const config = window.MINOVA_CINEMA_CONFIG || {};
  const form = document.querySelector("#publicFeedbackForm");
  const typeButtons = Array.from(document.querySelectorAll("[data-feedback-type]"));
  const typeInput = document.querySelector("#feedbackType");
  const subject = document.querySelector("#feedbackSubject");
  const description = document.querySelector("#feedbackDescription");
  const count = document.querySelector("#feedbackCount");
  const status = document.querySelector("#feedbackStatus");
  const submit = document.querySelector("#feedbackSubmit");

  const setType = (requested) => {
    const type = requested === "feature" ? "feature" : "bug";
    typeInput.value = type;
    subject.placeholder = type === "feature"
      ? "What should Minova Cinema add?"
      : "What went wrong?";
    typeButtons.forEach((button) => {
      const active = button.dataset.feedbackType === type;
      button.classList.toggle("active", active);
      button.setAttribute("aria-pressed", String(active));
    });
  };

  const setStatus = (message = "", state = "") => {
    status.textContent = message;
    status.className = `form-status ${state}`.trim();
  };

  typeButtons.forEach((button) => {
    button.addEventListener("click", () => setType(button.dataset.feedbackType));
  });
  description?.addEventListener("input", () => {
    count.textContent = String(description.value.length);
  });

  form?.addEventListener("submit", async (event) => {
    event.preventDefault();
    if (!form.reportValidity()) return;
    submit.disabled = true;
    setStatus("Sending your feedback securely...");

    const data = new FormData(form);
    const payload = {
      product: "Minova Cinema",
      type: String(data.get("type") || "bug"),
      name: String(data.get("name") || "").trim().slice(0, 80),
      email: String(data.get("email") || "").trim(),
      subject: String(data.get("subject") || "").trim(),
      description: String(data.get("description") || "").trim(),
      appVersion: String(data.get("appVersion") || config.currentVersion || "").trim(),
      device: String(data.get("device") || "").trim(),
      androidVersion: String(data.get("androidVersion") || "").trim(),
      website: String(data.get("website") || "").trim()
    };

    try {
      let response;
      if (config.feedbackEndpoint) {
        response = await fetch(config.feedbackEndpoint, {
          method: "POST",
          headers: { "content-type": "application/json" },
          body: JSON.stringify(payload)
        });
      } else {
        const relay = {
          name: payload.name || "Minova Cinema user",
          product: payload.product,
          type: payload.type === "feature" ? "Feature request" : "Bug report",
          subject: payload.subject,
          message: payload.description,
          version: payload.appVersion,
          device: payload.device || "Not provided",
          android_version: payload.androidVersion || "Not provided",
          _subject: `[Minova Cinema ${payload.type === "feature" ? "Feature Request" : "Bug Report"}] ${payload.subject}`,
          _template: "table",
          _honey: payload.website
        };
        if (payload.email) relay.email = payload.email;
        response = await fetch(`https://formsubmit.co/ajax/${encodeURIComponent(config.feedbackRecipient || "minova.chromium@gmail.com")}`, {
          method: "POST",
          headers: { "content-type": "application/json", accept: "application/json" },
          body: JSON.stringify(relay)
        });
      }

      const result = await response.json().catch(() => ({}));
      if (!response.ok || result.success === false) {
        throw new Error(result.message || "Feedback could not be sent.");
      }
      setStatus("Thank you. Your feedback was sent to the Minova team.", "success");
      form.reset();
      setType("bug");
      count.textContent = "0";
    } catch (error) {
      setStatus(error.message || "Feedback could not be sent. Please try again.", "error");
    } finally {
      submit.disabled = false;
    }
  });

  setType(new URL(window.location.href).searchParams.get("type"));
})();
