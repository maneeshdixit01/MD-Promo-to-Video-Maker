
const $ = (id) => document.getElementById(id);
const mediaFiles = [];

function makeScenes(prompt, language) {
  const p = prompt.trim();
  const isEnglish = language === "en-US";
  const words = p.split(/\s+/).filter(Boolean);
  const subject = words.length > 12
    ? words.slice(0, 12).join(" ") + "…"
    : p;

  if (isEnglish) {
    return [
      ["Hook", "Stop scrolling! Discover something special today.", "0–6 sec"],
      ["Introduce", `Present the main offer: ${subject}`, "6–12 sec"],
      ["Benefits", "Show 2–3 clear benefits with bold animated text.", "12–18 sec"],
      ["Trust", "Use your real product photos, customer moments or key details.", "18–24 sec"],
      ["Call to action", "Visit us today. Contact now for details!", "24–30 sec"]
    ];
  }

  return [
    ["ध्यान खींचें", "रुकिए! आपके लिए एक खास पेशकश आई है।", "0–6 सेकंड"],
    ["परिचय", `मुख्य बात दिखाएँ: ${subject}`, "6–12 सेकंड"],
    ["फायदे", "2–3 बड़े फायदे आकर्षक टेक्स्ट और एनिमेशन के साथ दिखाएँ।", "12–18 सेकंड"],
    ["भरोसा", "अपने असली प्रोडक्ट फोटो, खासियत या ग्राहक अनुभव दिखाएँ।", "18–24 सेकंड"],
    ["एक्शन", "आज ही संपर्क करें! अधिक जानकारी के लिए अभी जुड़ें।", "24–30 सेकंड"]
  ];
}

function renderScenes(scenes) {
  $("scenes").innerHTML = scenes.map((s, i) =>
    `<div class="scene">
      <div class="scene-no">${i + 1}</div>
      <div>
        <b>${escapeHtml(s[0])}</b>
        <p>${escapeHtml(s[1])}</p>
        <small>${escapeHtml(s[2])}</small>
      </div>
    </div>`
  ).join("");
}

function escapeHtml(value) {
  return String(value).replace(/[&<>"']/g, c =>
    ({
      "&": "&amp;",
      "<": "&lt;",
      ">": "&gt;",
      '"': "&quot;",
      "'": "&#39;"
    }[c])
  );
}

$("makeBtn").addEventListener("click", () => {
  const prompt = $("prompt").value.trim();

  if (!prompt) {
    $("engineStatus").textContent = "पहले वीडियो का आइडिया लिखें।";
    $("prompt").focus();
    return;
  }

  const language = $("language").value;
  const scenes = makeScenes(prompt, language);

  renderScenes(scenes);

  $("engineStatus").textContent =
    "Smart Offline Engine ने 5 सीन की योजना तैयार की है। Gemini कनेक्शन उपलब्ध न होने पर भी काम जारी रहेगा।";

  $("scenesPanel").scrollIntoView({
    behavior: "smooth",
    block: "start"
  });
});

$("mediaInput").addEventListener("change", (event) => {
  const selected = Array.from(event.target.files || []);

  selected.forEach(file => {
    if (
      !file.type.startsWith("image/") &&
      !file.type.startsWith("video/")
    ) return;

    const url = URL.createObjectURL(file);
    mediaFiles.push({ file, url });
  });

  $("mediaCount").textContent = `${mediaFiles.length} फाइल`;

  $("mediaGrid").innerHTML = mediaFiles.map(item => {
    const tag = item.file.type.startsWith("video/")
      ? "video"
      : "img";

    return `
      <div class="media-item">
        <${tag} src="${item.url}" ${tag === "video" ? "muted" : ""}></${tag}>
        <span>${escapeHtml(item.file.name)}</span>
      </div>
    `;
  }).join("");
});

window.addEventListener("beforeunload", () => {
  mediaFiles.forEach(item => URL.revokeObjectURL(item.url));
});
