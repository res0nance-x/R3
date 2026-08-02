package r3.http

import r3.org.json.JSONArray

val dirHTMLTemplate = { json: JSONArray ->
	"""
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Directory Listing</title>
<link href="https://fonts.googleapis.com/css2?family=Outfit:wght@300;400;500;600&display=swap" rel="stylesheet">
<style>
:root {
--bg-gradient: linear-gradient(135deg, #0f172a 0%, #1e1b4b 100%);
--panel-bg: rgba(30, 41, 59, 0.7);
--panel-border: rgba(255, 255, 255, 0.08);
--text-primary: #f8fafc;
--text-secondary: #94a3b8;
--accent: #6366f1;
--accent-hover: #4f46e5;
--accent-glow: rgba(99, 102, 241, 0.4);
--item-hover-bg: rgba(255, 255, 255, 0.03);
}

body {
margin: 0;
padding: 0;
background: var(--bg-gradient);
min-height: 100vh;
font-family: 'Outfit', sans-serif;
color: var(--text-primary);
display: flex;
justify-content: center;
align-items: flex-start;
box-sizing: border-box;
}

main {
width: 100%;
max-width: 800px;
margin: 40px 20px;
background: var(--panel-bg);
backdrop-filter: blur(12px);
-webkit-backdrop-filter: blur(12px);
border: 1px solid var(--panel-border);
border-radius: 16px;
padding: 32px;
box-shadow: 0 20px 40px rgba(0, 0, 0, 0.3);
}

h1 {
font-size: 1.8rem;
font-weight: 600;
margin-top: 0;
margin-bottom: 8px;
background: linear-gradient(90deg, #a5b4fc, #818cf8);
-webkit-background-clip: text;
-webkit-text-fill-color: transparent;
}

.meta-info {
font-size: 0.95rem;
color: var(--text-secondary);
margin-bottom: 24px;
display: flex;
align-items: center;
gap: 8px;
}

ul {
list-style: none;
padding: 0;
margin: 0;
border-radius: 12px;
overflow: hidden;
}

li {
border-bottom: 1px solid rgba(255, 255, 255, 0.04);
}

li:last-child {
border-bottom: none;
}

.file-item {
display: flex;
align-items: center;
padding: 14px 18px;
text-decoration: none;
color: var(--text-primary);
font-weight: 400;
font-size: 1.05rem;
transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
gap: 12px;
}

.file-item:hover {
background: var(--item-hover-bg);
padding-left: 24px;
color: var(--accent);
}

.icon {
display: flex;
align-items: center;
justify-content: center;
width: 24px;
height: 24px;
transition: transform 0.2s ease;
}

.file-item:hover .icon {
transform: scale(1.15);
}

#loader-overlay {
position: fixed;
top: 0;
left: 0;
width: 100vw;
height: 100vh;
background: rgba(15, 23, 42, 0.7);
backdrop-filter: blur(8px);
-webkit-backdrop-filter: blur(8px);
display: flex;
flex-direction: column;
justify-content: center;
align-items: center;
z-index: 9999;
opacity: 0;
pointer-events: none;
transition: opacity 0.3s ease;
}

#loader-overlay.active {
opacity: 1;
pointer-events: auto;
}

.spinner-box {
position: relative;
display: flex;
justify-content: center;
align-items: center;
margin-bottom: 20px;
}

.spinner-outer {
width: 64px;
height: 64px;
border: 3px solid transparent;
border-top-color: var(--accent);
border-bottom-color: var(--accent);
border-radius: 50%;
animation: spin 1.2s cubic-bezier(0.5, 0, 0.5, 1) infinite;
}

.spinner-inner {
position: absolute;
width: 44px;
height: 44px;
border: 3px solid transparent;
border-left-color: #818cf8;
border-right-color: #818cf8;
border-radius: 50%;
animation: spin-reverse 1.2s cubic-bezier(0.5, 0, 0.5, 1) infinite;
}

.loader-text {
font-size: 1.2rem;
font-weight: 500;
color: var(--text-primary);
text-shadow: 0 2px 10px rgba(0, 0, 0, 0.5);
display: flex;
flex-direction: column;
align-items: center;
gap: 8px;
}

.loader-subtext {
font-size: 0.95rem;
color: var(--text-secondary);
}

@keyframes spin {
0% { transform: rotate(0deg); }
100% { transform: rotate(360deg); }
}

@keyframes spin-reverse {
0% { transform: rotate(0deg); }
100% { transform: rotate(-360deg); }
}
</style>
</head>
<body>

<main>
<h1>Directory Index</h1>
<div class="meta-info" id="meta-info">Items - Loading...</div>
<ul id="file-list"></ul>
</main>

<div id="loader-overlay">
<div class="spinner-box">
<div class="spinner-outer"></div>
<div class="spinner-inner"></div>
</div>
<div class="loader-text">
<span class="loader-subtext" id="loader-target">Loading ...</span>
</div>
</div>

<script>
// Ensure directory paths always end with a slash for relative link resolution
if (!window.location.pathname.endsWith("/")) {
    window.location.replace(window.location.pathname + "/" + window.location.search + window.location.hash);
}

const data = ${json.toString(2)};

// Update meta info
const metaInfo = document.getElementById("meta-info");
metaInfo.innerText = "Items: " + data.length;

// Populate file list
const list = document.getElementById("file-list");
data.forEach(file => {
const name = file.name;
const isDir = name.endsWith("/");

const li = document.createElement("li");
const a = document.createElement("a");
a.href = name;
a.className = "file-item";

// Folder vs File SVG icon
const iconSpan = document.createElement("span");
iconSpan.className = "icon";
if (isDir) {
iconSpan.innerHTML = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor" width="20" height="20" style="color: #fbbf24;"><path d="M19.5 21a3 3 0 003-3v-4.5a3 3 0 00-3-3h-1.5V9a3 3 0 00-3-3h-3.379a3 3 0 01-2.122-.879L8.379 3.879A3 3 0 006.257 3H4.5a3 3 0 00-3 3v12a3 3 0 003 3h15z"/></svg>`;
} else {
iconSpan.innerHTML = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor" width="20" height="20" style="color: #94a3b8;"><path fill-rule="evenodd" d="M5.625 1.5c-1.036 0-1.875.84-1.875 1.875v17.25c0 1.035.84 1.875 1.875 1.875h12.75c1.035 0 1.875-.84 1.875-1.875V12.75A3.75 3.75 0 0016.5 9h-1.875a1.875 1.875 0 01-1.875-1.875V5.25A3.75 3.75 0 009 1.5H5.625zM7.5 15a.75.75 0 01.75-.75h7.5a.75.75 0 010 1.5h-7.5A.75.75 0 017.5 15zm.75 2.25a.75.75 0 000 1.5h7.5a.75.75 0 000-1.5h-7.5z" clip-rule="evenodd" /><path d="M12.971 1.816A5.23 5.23 0 0114.25 5.25v1.875c0 .207.168.375.375.375H16.5a5.23 5.23 0 013.434 1.279 9.768 9.768 0 00-6.963-6.963z" /></svg>`;
}

const textSpan = document.createElement("span");
textSpan.innerText = name;

a.appendChild(iconSpan);
a.appendChild(textSpan);
li.appendChild(a);
list.appendChild(li);

// Wait indicator click handling
a.addEventListener("click", (e) => {
if (isDir) {
const overlay = document.getElementById("loader-overlay");
const targetLabel = document.getElementById("loader-target");
targetLabel.innerText = "Loading: " + name;
overlay.classList.add("active");
}
});
});
</script>
</body>
</html>
			""".trimIndent()
}