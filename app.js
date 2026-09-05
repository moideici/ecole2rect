const dashboard = document.querySelector("#dashboard");
const editButton = document.querySelector("#edit-dashboard");
const toolbar = document.querySelector("#dashboard-toolbar");
const setupModal = document.querySelector("#setup-modal");
const securityModal = document.querySelector("#security-modal");
const toast = document.querySelector("#toast");
const toastMessage = document.querySelector("#toast-message");

function showToast(message) {
  toastMessage.textContent = message;
  toast.classList.remove("hidden");
  window.setTimeout(() => toast.classList.add("hidden"), 2400);
}

editButton.addEventListener("click", () => {
  dashboard.classList.add("editing");
  toolbar.classList.remove("hidden");
  editButton.classList.add("hidden");
});

document.querySelector("#save-dashboard").addEventListener("click", () => {
  dashboard.classList.remove("editing");
  toolbar.classList.add("hidden");
  editButton.classList.remove("hidden");
  showToast("Accueil sauvegardé");
});

document.querySelector("#add-widget").addEventListener("click", () => {
  showToast("Catalogue de widgets — bientôt disponible");
});

document.querySelectorAll("[data-close-modal]").forEach((button) => {
  button.addEventListener("click", () => setupModal.classList.add("hidden"));
});

document.querySelector("#setup-form").addEventListener("submit", (event) => {
  event.preventDefault();
  setupModal.classList.add("hidden");
  showToast("Connexion enregistrée sur cet appareil");
});

document.querySelector("#show-security").addEventListener("click", () => {
  setupModal.classList.add("hidden");
  securityModal.classList.remove("hidden");
});

document.querySelectorAll("[data-close-security]").forEach((button) => {
  button.addEventListener("click", () => securityModal.classList.add("hidden"));
});

document.querySelector("#security-form").addEventListener("submit", (event) => {
  event.preventDefault();
  securityModal.classList.add("hidden");
  showToast("Réponse enregistrée localement");
});

document.querySelector(".brand").addEventListener("click", (event) => {
  event.preventDefault();
  setupModal.classList.remove("hidden");
});

document.querySelectorAll(".nav-link[data-view]").forEach((link) => {
  link.addEventListener("click", () => {
    document.querySelectorAll(".nav-link").forEach((item) => item.classList.remove("active"));
    link.classList.add("active");
    const view = link.dataset.view;
    const visibleWidgets = {
      accueil: ["next-class", "day", "grades", "homework", "focus", "messages"],
      planning: ["next-class", "day"],
      devoirs: ["homework"],
      notes: ["grades"],
      messages: ["messages"],
    }[view];
    document.querySelectorAll(".widget").forEach((widget) => {
      widget.classList.toggle("hidden", !visibleWidgets.includes(widget.dataset.widget));
    });
    toolbar.classList.add("hidden");
    dashboard.classList.remove("editing");
    editButton.classList.toggle("hidden", view !== "accueil");
  });
});
