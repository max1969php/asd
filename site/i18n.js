// Lingue del sito: le stesse dell'app. Una lingua compare nella select solo con ready: true,
// cioè quando esiste i18n/<code>.json (stesse chiavi di it.json; le chiavi mancanti restano in italiano).
const LANGUAGES = [
  { code: "it", name: "Italiano", ready: true },
  { code: "en", name: "English" },
  { code: "bg", name: "Български" },
  { code: "cs", name: "Čeština" },
  { code: "da", name: "Dansk" },
  { code: "de", name: "Deutsch" },
  { code: "el", name: "Ελληνικά" },
  { code: "es", name: "Español" },
  { code: "et", name: "Eesti" },
  { code: "fi", name: "Suomi" },
  { code: "fr", name: "Français" },
  { code: "ga", name: "Gaeilge" },
  { code: "hr", name: "Hrvatski" },
  { code: "hu", name: "Magyar" },
  { code: "lt", name: "Lietuvių" },
  { code: "lv", name: "Latviešu" },
  { code: "mt", name: "Malti" },
  { code: "nl", name: "Nederlands" },
  { code: "pl", name: "Polski" },
  { code: "pt", name: "Português" },
  { code: "ro", name: "Română" },
  { code: "sk", name: "Slovenčina" },
  { code: "sl", name: "Slovenščina" },
  { code: "sv", name: "Svenska" },
  { code: "nb", name: "Norsk bokmål" },
  { code: "uk", name: "Українська" },
  { code: "zh-CN", name: "简体中文" },
  { code: "hi", name: "हिन्दी" },
  { code: "ar", name: "العربية", rtl: true },
  { code: "fa", name: "فارسی", rtl: true },
  { code: "sw", name: "Kiswahili" },
  { code: "wo", name: "Wolof" },
  { code: "ha", name: "Hausa" },
  { code: "yo", name: "Yorùbá" },
  { code: "ig", name: "Igbo" },
  { code: "am", name: "አማርኛ" },
  { code: "ti", name: "ትግርኛ" },
  { code: "so", name: "Soomaali" },
];

const FALLBACK = "it";
const ready = LANGUAGES.filter((l) => l.ready);

function remembered() {
  try { return localStorage.getItem("asd-lang"); } catch { return null; }
}

function remember(code) {
  try { localStorage.setItem("asd-lang", code); } catch { /* navigazione privata: pazienza */ }
}

/** ?lang=xx, poi l'ultima scelta, poi la lingua del browser, poi l'italiano. */
function pickLanguage() {
  const isReady = (c) => ready.find((l) => l.code.toLowerCase() === (c || "").toLowerCase());
  const fromUrl = new URLSearchParams(location.search).get("lang");
  if (isReady(fromUrl)) return isReady(fromUrl).code;
  if (isReady(remembered())) return isReady(remembered()).code;
  for (const tag of navigator.languages || [navigator.language]) {
    const hit = isReady(tag) || isReady(tag.split("-")[0]) || (tag.startsWith("zh") && isReady("zh-CN"));
    if (hit) return hit.code;
  }
  return FALLBACK;
}

async function load(code) {
  const res = await fetch(`i18n/${code}.json`, { cache: "no-cache" });
  if (!res.ok) throw new Error(`${code}: ${res.status}`);
  return res.json();
}

async function apply(code) {
  const base = await load(FALLBACK);
  const strings = code === FALLBACK ? base : { ...base, ...(await load(code).catch(() => ({}))) };
  const lang = LANGUAGES.find((l) => l.code === code);
  document.documentElement.lang = code;
  document.documentElement.dir = lang && lang.rtl ? "rtl" : "ltr";
  // I testi sono nostri (file del sito), quindi possono contenere <strong>, <code>, <a>.
  document.querySelectorAll("[data-i18n]").forEach((el) => {
    const value = strings[el.dataset.i18n];
    if (value !== undefined) el.innerHTML = value;
  });
  document.querySelectorAll("[data-i18n-attr]").forEach((el) => {
    const [attr, key] = el.dataset.i18nAttr.split(":");
    if (strings[key] !== undefined) el.setAttribute(attr, strings[key]);
  });
  if (strings["meta.title"]) document.title = strings["meta.title"];
  copyLabels = [strings["code.copy"] || "copy", strings["code.copied"] || "ok"];
  document.querySelectorAll("button.copy").forEach((b) => (b.textContent = copyLabels[0]));
}

let copyLabels = ["copia", "copiato"];

function addCopyButtons() {
  document.querySelectorAll("pre").forEach((pre) => {
    const button = document.createElement("button");
    button.className = "copy";
    button.type = "button";
    button.textContent = copyLabels[0];
    button.addEventListener("click", async () => {
      try {
        await navigator.clipboard.writeText(pre.querySelector("code").textContent);
        button.textContent = copyLabels[1];
        setTimeout(() => (button.textContent = copyLabels[0]), 1500);
      } catch { /* clipboard negata: si copia a mano */ }
    });
    pre.appendChild(button);
  });
}

function setupSelect(current) {
  const select = document.getElementById("lang");
  ready.forEach((l) => select.add(new Option(l.name, l.code, false, l.code === current)));
  select.closest(".lang").hidden = ready.length < 2;
  select.addEventListener("change", () => {
    remember(select.value);
    apply(select.value);
  });
}

const initial = pickLanguage();
addCopyButtons();
setupSelect(initial);
apply(initial);
