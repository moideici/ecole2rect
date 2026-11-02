const dashboard = document.querySelector("#dashboard");
const editButton = document.querySelector("#edit-dashboard");
const toolbar = document.querySelector("#dashboard-toolbar");
const setupModal = document.querySelector("#setup-modal");
const securityModal = document.querySelector("#security-modal");
const toast = document.querySelector("#toast");
const toastMessage = document.querySelector("#toast-message");

const VIEWS = {
  accueil: ["next-class", "day", "grades", "homework", "focus", "messages"],
  planning: ["next-class", "day"],
  devoirs: ["homework"],
  notes: ["grades"],
  messages: ["messages"],
};
const ORDER_KEY = "ecole2recte.dashboard-order";

// Le stockage peut être refusé (navigation privée, fichier ouvert en local) : la maquette doit marcher sans.
function readOrder() {
  try {
    const order = JSON.parse(localStorage.getItem(ORDER_KEY));
    return Array.isArray(order) ? order : [];
  } catch {
    return [];
  }
}

function writeOrder(order) {
  try {
    localStorage.setItem(ORDER_KEY, JSON.stringify(order));
    return true;
  } catch {
    return false;
  }
}

let toastTimer;
function showToast(message) {
  toastMessage.textContent = message;
  toast.classList.remove("hidden");
  // Sans cela, le minuteur d'un toast précédent masque le suivant avant l'heure.
  window.clearTimeout(toastTimer);
  toastTimer = window.setTimeout(() => toast.classList.add("hidden"), 2400);
}

/* Modales */

let returnFocus = null;

function openModal(modal) {
  const alreadyOpen = document.querySelector(".modal-backdrop:not(.hidden)");
  if (alreadyOpen) alreadyOpen.classList.add("hidden");
  else returnFocus = document.activeElement;
  modal.classList.remove("hidden");
  modal.querySelector("input, select")?.focus();
}

function closeModals() {
  document.querySelectorAll(".modal-backdrop").forEach((modal) => modal.classList.add("hidden"));
  returnFocus?.focus();
  returnFocus = null;
}

document.querySelectorAll("[data-open-setup]").forEach((trigger) => {
  trigger.addEventListener("click", (event) => {
    event.preventDefault();
    openModal(setupModal);
  });
});

document.querySelectorAll("[data-close-modal]").forEach((button) => {
  button.addEventListener("click", closeModals);
});

document.querySelectorAll(".modal-backdrop").forEach((backdrop) => {
  backdrop.addEventListener("click", (event) => {
    if (event.target === backdrop) closeModals();
  });
});

document.addEventListener("keydown", (event) => {
  if (event.key === "Escape" && document.querySelector(".modal-backdrop:not(.hidden)")) closeModals();
});

document.querySelector("#show-security").addEventListener("click", () => openModal(securityModal));

document.querySelector("#setup-form").addEventListener("submit", (event) => {
  event.preventDefault();
  closeModals();
  showToast("Maquette : rien n'a été envoyé");
});

document.querySelector("#security-form").addEventListener("submit", (event) => {
  event.preventDefault();
  closeModals();
  showToast("Maquette : réponse non envoyée");
});

/* Personnalisation de l'accueil */

function currentOrder() {
  return [...dashboard.querySelectorAll(".widget")].map((widget) => widget.dataset.widget);
}

function applyOrder(order) {
  // Un widget absent de l'ordre enregistré garde sa place relative, à la fin.
  const rank = (widget) => {
    const index = order.indexOf(widget.dataset.widget);
    return index === -1 ? order.length : index;
  };
  [...dashboard.querySelectorAll(".widget")]
    .sort((a, b) => rank(a) - rank(b))
    .forEach((widget) => dashboard.append(widget));
}

function setEditing(editing) {
  dashboard.classList.toggle("editing", editing);
  toolbar.classList.toggle("hidden", !editing);
  editButton.classList.toggle("hidden", editing);
  dashboard.querySelectorAll(".widget").forEach((widget) => {
    widget.draggable = editing;
    if (editing) widget.tabIndex = 0;
    else widget.removeAttribute("tabindex");
  });
}

editButton.addEventListener("click", () => setEditing(true));

document.querySelector("#save-dashboard").addEventListener("click", () => {
  setEditing(false);
  showToast(writeOrder(currentOrder()) ? "Accueil sauvegardé sur ce navigateur" : "Ordre gardé pour cette visite seulement");
});

document.querySelector("#add-widget").addEventListener("click", () => {
  showToast("Catalogue de widgets — bientôt disponible");
});

let dragged = null;
let dropTarget = null;

function markDropTarget(widget) {
  dropTarget?.classList.remove("drop-target");
  dropTarget = widget;
  dropTarget?.classList.add("drop-target");
}

dashboard.addEventListener("dragstart", (event) => {
  dragged = event.target.closest?.(".widget");
  if (!dragged) return;
  event.dataTransfer.effectAllowed = "move";
  // Firefox n'entame pas le glisser sans donnée attachée.
  event.dataTransfer.setData("text/plain", dragged.dataset.widget);
  dragged.classList.add("dragging");
});

dashboard.addEventListener("dragover", (event) => {
  if (!dragged) return;
  const target = event.target.closest(".widget");
  if (!target || target === dragged) {
    markDropTarget(null);
    return;
  }
  event.preventDefault();
  event.dataTransfer.dropEffect = "move";
  markDropTarget(target);
});

dashboard.addEventListener("drop", (event) => {
  const target = event.target.closest(".widget");
  if (!dragged || !target || target === dragged) return;
  event.preventDefault();
  // La carte prend la place de la cible, que la cible soit avant ou après elle.
  const targetIsBefore = dragged.compareDocumentPosition(target) & Node.DOCUMENT_POSITION_PRECEDING;
  if (targetIsBefore) target.before(dragged);
  else target.after(dragged);
});

dashboard.addEventListener("dragend", () => {
  dragged?.classList.remove("dragging");
  dragged = null;
  markDropTarget(null);
});

// Le glisser-déposer n'est pas utilisable au clavier : les flèches déplacent la carte sélectionnée.
dashboard.addEventListener("keydown", (event) => {
  if (!dashboard.classList.contains("editing")) return;
  const widget = event.target;
  if (!widget.classList?.contains("widget")) return;
  if ((event.key === "ArrowUp" || event.key === "ArrowLeft") && widget.previousElementSibling) {
    widget.previousElementSibling.before(widget);
  } else if ((event.key === "ArrowDown" || event.key === "ArrowRight") && widget.nextElementSibling) {
    widget.nextElementSibling.after(widget);
  } else {
    return;
  }
  event.preventDefault();
  widget.focus();
});

/* Vues */

// La vue suit l'adresse : les liens des cartes, le rechargement et le bouton retour tombent juste.
function showView(requested) {
  const view = VIEWS[requested] ? requested : "accueil";
  document.querySelectorAll(".nav-link[data-view]").forEach((link) => {
    const active = link.dataset.view === view;
    link.classList.toggle("active", active);
    if (active) link.setAttribute("aria-current", "page");
    else link.removeAttribute("aria-current");
  });
  document.querySelectorAll(".widget").forEach((widget) => {
    widget.classList.toggle("hidden", !VIEWS[view].includes(widget.dataset.widget));
  });
  setEditing(false);
  editButton.classList.toggle("hidden", view !== "accueil");
}

window.addEventListener("hashchange", () => showView(location.hash.slice(1)));

applyOrder(readOrder());
showView(location.hash.slice(1));
