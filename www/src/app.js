const $ = (id) => document.getElementById(id);

const mediaFiles = [];
let activeScenes = [];

const NativeVideoExporter = window.Capacitor?.registerPlugin
  ? window.Capacitor.registerPlugin("NativeVideoExporter")
  : null;

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

// Create storyboard
$("makeBtn").addEventListener("click", () => {
  const prompt = $("prompt").value.trim();

  if (!prompt) {
    $("engineStatus").textContent =
      "पहले वीडियो का आइडिया लिखें।";
    $("prompt").focus();
    return;
  }

  const language = $("language").value;

  activeScenes = makeScenes(prompt, language);

  renderScenes(activeScenes);

  $("exportBtn").disabled = false;
  $("exportBtn").textContent = "🎬 MP4 वीडियो एक्सपोर्ट करें";

  $("engineStatus").textContent =
    "Smart Offline Engine ने 5 सीन की योजना तैयार कर दी है।";

  $("scenesPanel").scrollIntoView({
    behavior: "smooth",
    block: "start"
  });
});

// Export MP4 video
$("exportBtn").addEventListener("click", async () => {

  if (!activeScenes.length) {
    $("engineStatus").textContent =
      "पहले वीडियो की योजना बनाएँ।";
    return;
  }

  if (!window.Capacitor?.isNativePlatform?.()) {
    $("engineStatus").textContent =
      "MP4 Export के लिए Android APK इस्तेमाल करें।";
    return;
  }

  if (!NativeVideoExporter) {
    $("engineStatus").textContent =
      "Native Video Exporter उपलब्ध नहीं है। APK दोबारा Build करें।";
    return;
  }

  $("exportBtn").disabled = true;
  $("exportBtn").textContent = "⏳ वीडियो तैयार हो रही है...";
  $("engineStatus").textContent =
    "कृपया प्रतीक्षा करें। 30 सेकंड की MP4 वीडियो बनाई जा रही है।";

  try {

    const scenes = activeScenes.map(scene => ({
      title: scene[0],
      text: scene[1]
    }));

    const result = await NativeVideoExporter.exportVideo({
      format: $("format").value,
      scenes: scenes
    });

    if (!result || !result.success) {
      throw new Error(
        result?.message || "वीडियो Export नहीं हो सकी।"
      );
    }

    $("engineStatus").textContent =
      "✅ " + (result.message || "वीडियो Gallery में सेव हो गई।");

    $("exportBtn").textContent =
      "🎬 दोबारा MP4 Export करें";

  } catch (error) {

    console.error("Video Export Error:", error);

    $("engineStatus").textContent =
      "वीडियो Export असफल: " +
      (error?.message || "अज्ञात समस्या");

    $("exportBtn").textContent =
      "🔄 फिर से कोशिश करें";

  } finally {
    $("exportBtn").disabled = false;
  }
});

// Select photos and videos
$("mediaInput").addEventListener("change", (event) => {

  const selected = Array.from(event.target.files || []);

  selected.forEach(file => {

    if (
      !file.type.startsWith("image/") &&
      !file.type.startsWith("video/")
    ) return;

    const url = URL.createObjectURL(file);

    mediaFiles.push({
      file,
      url
    });
  });

  $("mediaCount").textContent =
    `${mediaFiles.length} फाइल`;

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
  mediaFiles.forEach(item => {
    URL.revokeObjectURL(item.url);
  });
});
