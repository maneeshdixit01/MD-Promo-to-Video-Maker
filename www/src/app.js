
const $ = (id) => document.getElementById(id);

const mediaFiles = [];
let activeScenes = [];

const NativeVideoExporter = window.Capacitor?.registerPlugin
  ? window.Capacitor.registerPlugin("NativeVideoExporter")
  : null;

function setStatus(message, type = "") {
  const status = $("engineStatus");
  status.textContent = message;
  status.className = "status " + type;
}

function makeScenes(prompt, language) {
  const topic = prompt.trim();

  if (language === "en-US") {
    return [
      ["INTRODUCTION", `Discover something amazing about ${topic}`],
      ["THE PROBLEM", "Looking for something better and more reliable?"],
      ["OUR SOLUTION", "We bring you quality, trust and convenience."],
      ["WHY CHOOSE US?", "A simple choice for a better experience."],
      ["CALL TO ACTION", "Contact us today and get started!"]
    ];
  }

  if (language === "hi-IN,en-US") {
    return [
      ["आपका स्वागत है", `${topic} के बारे में जानिए`],
      ["आपकी जरूरत", "क्या आप बेहतर विकल्प की तलाश में हैं?"],
      ["हमारा समाधान", "आपके लिए गुणवत्ता और भरोसे का शानदार विकल्प।"],
      ["हमें क्यों चुनें?", "बेहतर अनुभव और शानदार सुविधा।"],
      ["आज ही संपर्क करें", "अभी शुरुआत करें!"]
    ];
  }

  return [
    ["आपका स्वागत है", `${topic} के बारे में जानिए`],
    ["आपकी जरूरत", "क्या आप किसी बेहतर विकल्प की तलाश में हैं?"],
    ["हमारा समाधान", "हम आपके लिए लाए हैं गुणवत्ता और भरोसा।"],
    ["हमें क्यों चुनें?", "बेहतर अनुभव और शानदार सुविधा।"],
    ["आज ही संपर्क करें", "अभी शुरुआत करें और लाभ उठाएं!"]
  ];
}

function renderScenes(scenes) {
  const container = $("scenes");
  container.replaceChildren();

  scenes.forEach((scene, index) => {
    const card = document.createElement("article");
    card.className = "scene-card";

    const number = document.createElement("span");
    number.className = "scene-number";
    number.textContent = `SCENE ${index + 1}`;

    const title = document.createElement("h3");
    title.textContent = scene[0];

    const description = document.createElement("p");
    description.textContent = scene[1];

    const duration = document.createElement("small");
    duration.textContent = "6 सेकंड";

    card.append(number, title, description, duration);
    container.appendChild(card);
  });
}

$("makeBtn").addEventListener("click", () => {
  const prompt = $("prompt").value.trim();
  const language = $("language").value;

  if (!prompt) {
    setStatus("कृपया पहले वीडियो का विषय लिखें।", "error");
    $("prompt").focus();
    return;
  }

  activeScenes = makeScenes(prompt, language);
  renderScenes(activeScenes);

  $("scenesPanel").innerHTML =
    "<p class='success-text'>आपका 5 सीन का स्टोरीबोर्ड तैयार है।</p>";

  $("exportBtn").disabled = false;
  setStatus("स्टोरीबोर्ड तैयार है। अब MP4 एक्सपोर्ट कर सकते हैं।", "success");
});

$("mediaInput").addEventListener("change", (event) => {
  const files = Array.from(event.target.files || []);

  mediaFiles.forEach((item) => URL.revokeObjectURL(item.url));
  mediaFiles.length = 0;

  files.forEach((file) => {
    mediaFiles.push({
      file,
      url: URL.createObjectURL(file)
    });
  });

  $("mediaCount").textContent =
    mediaFiles.length
      ? `${mediaFiles.length} फाइल चुनी गई हैं।`
      : "अभी कोई मीडिया नहीं चुना गया है।";

  const grid = $("mediaGrid");
  grid.replaceChildren();

  mediaFiles.forEach((item) => {
    const preview = document.createElement("div");
    preview.className = "media-item";

    if (item.file.type.startsWith("image/")) {
      const image = document.createElement("img");
      image.src = item.url;
      image.alt = item.file.name;
      preview.appendChild(image);
    } else {
      const video = document.createElement("video");
      video.src = item.url;
      video.muted = true;
      video.controls = true;
      preview.appendChild(video);
    }

    const name = document.createElement("small");
    name.textContent = item.file.name;
    preview.appendChild(name);

    grid.appendChild(preview);
  });
});

$("exportBtn").addEventListener("click", async () => {
  if (!activeScenes.length) {
    setStatus("पहले स्टोरीबोर्ड बनाएं।", "error");
    return;
  }

  if (!window.Capacitor?.isNativePlatform?.()) {
    setStatus("MP4 एक्सपोर्ट के लिए Android APK खोलें।", "error");
    return;
  }

  if (!NativeVideoExporter?.exportVideo) {
    setStatus("Native Video Exporter Plugin उपलब्ध नहीं है।", "error");
    return;
  }

  const button = $("exportBtn");
  button.disabled = true;
  button.textContent = "वीडियो तैयार हो रही है...";
  setStatus("कृपया प्रतीक्षा करें। 30 सेकंड की वीडियो बनाई जा रही है।");

  try {
    const result = await NativeVideoExporter.exportVideo({
      format: $("format").value,
      scenes: activeScenes.map((scene) => ({
        title: scene[0],
        text: scene[1]
      }))
    });

    const message = result?.message || "वीडियो सफलतापूर्वक तैयार हुई।";
    setStatus(message, "success");
  } catch (error) {
    console.error("Video export error:", error);
    setStatus(
      "वीडियो एक्सपोर्ट नहीं हो पाया: " +
      (error?.message || String(error)),
      "error"
    );
  } finally {
    button.disabled = false;
    button.textContent = "🎬 MP4 वीडियो एक्सपोर्ट करें";
  }
});

window.addEventListener("beforeunload", () => {
  mediaFiles.forEach((item) => URL.revokeObjectURL(item.url));
});
