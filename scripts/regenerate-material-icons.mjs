#!/usr/bin/env node

// Create the elevated color icons from the same SVG geometry as the Apple icon.
import { execFileSync } from "node:child_process";
import { mkdtemp, mkdir, readFile, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const webRoot = process.argv[2] ? resolve(process.argv[2]) : null;
const source = await readFile(join(root, "artwork/logdate-app-icon.svg"), "utf8");
const start = source.indexOf("  <g transform=\"translate(54.6, 69.6) scale(0.825)\">");
const end = source.lastIndexOf("</svg>");
if (start < 0 || end < start) throw new Error("Canonical icon structure changed");

const header = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1024 1024" width="1024" height="1024">\n';
const background = source.slice(source.indexOf("\n") + 1, start);
const photos = source.slice(start, end).replaceAll(
  '<g transform="rotate(',
  '<g filter="url(#photo-shadow)" transform="rotate(',
);
if ((photos.match(/filter="url\(#photo-shadow\)"/g) ?? []).length !== 2) {
  throw new Error("Expected two photo surfaces");
}
const shadow = `  <defs>
    <filter id="photo-shadow" x="-25%" y="-25%" width="160%" height="170%">
      <feGaussianBlur in="SourceAlpha" stdDeviation="18" result="ambient-blur"/>
      <feOffset in="ambient-blur" dx="0" dy="14" result="ambient-offset"/>
      <feFlood flood-color="#1E1312" flood-opacity="0.25" result="ambient-color"/>
      <feComposite in="ambient-color" in2="ambient-offset" operator="in" result="ambient-shadow"/>
      <feGaussianBlur in="SourceAlpha" stdDeviation="5" result="key-blur"/>
      <feOffset in="key-blur" dx="0" dy="5" result="key-offset"/>
      <feFlood flood-color="#1E1312" flood-opacity="0.16" result="key-color"/>
      <feComposite in="key-color" in2="key-offset" operator="in" result="key-shadow"/>
      <feMerge>
        <feMergeNode in="ambient-shadow"/>
        <feMergeNode in="key-shadow"/>
        <feMergeNode in="SourceGraphic"/>
      </feMerge>
    </filter>
  </defs>\n`;
const full = `${header}${shadow}${background}${photos}</svg>\n`;
// Adaptive masks show only the center of the 108dp foreground canvas.
const foreground = `${header}${shadow}  <g transform="translate(512 512) scale(0.75) translate(-512 -512)">\n${photos}  </g>\n</svg>\n`;
const maskable = `${header}${shadow}${background}  <g transform="translate(512 512) scale(0.72) translate(-512 -512)">\n${photos}  </g>\n</svg>\n`;
const rounded = `${header}${shadow}  <defs><clipPath id="desktop-shape"><rect width="1024" height="1024" rx="112"/></clipPath></defs>\n  <g clip-path="url(#desktop-shape)">\n${background}${photos}  </g>\n</svg>\n`;
const circular = `${header}${shadow}  <defs><clipPath id="round-shape"><circle cx="512" cy="512" r="512"/></clipPath></defs>\n  <g clip-path="url(#round-shape)">\n${background}${photos}  </g>\n</svg>\n`;
const artwork = join(root, "artwork/logdate-app-icon-material.svg");
await writeFile(artwork, full);

const temp = await mkdtemp(join(tmpdir(), "logdate-material-icons-"));
const render = (svg, png) => {
  execFileSync(process.execPath, [join(root, "scripts/render-svg-chrome.mjs"), svg, png]);
};
const resize = (input, output, pixels) => {
  execFileSync("sips", ["-z", String(pixels), String(pixels), input, "--out", output], {
    stdio: "ignore",
  });
};

try {
  for (const [name, svg] of Object.entries({ full, foreground, maskable, rounded, circular })) {
    await writeFile(join(temp, `${name}.svg`), svg);
    render(join(temp, `${name}.svg`), join(temp, `${name}.png`));
  }

  for (const platform of ["android-main", "wear"]) {
    const directory = join(root, `app/${platform}/src/main/res/drawable-nodpi`);
    await mkdir(directory, { recursive: true });
    await writeFile(join(directory, "ic_launcher_foreground_shadowed.png"),
      await readFile(join(temp, "foreground.png")));
  }

  for (const [density, pixels] of Object.entries({ mdpi: 48, hdpi: 72, xhdpi: 96, xxhdpi: 144, xxxhdpi: 192 })) {
    const directory = join(root, `app/android-main/src/main/res/mipmap-${density}`);
    resize(join(temp, "full.png"), join(directory, "ic_launcher.png"), pixels);
    resize(join(temp, "circular.png"), join(directory, "ic_launcher_round.png"), pixels);
  }

  resize(join(temp, "rounded.png"),
    join(root, "app/compose-main/src/commonMain/composeResources/drawable/ic_launcher_google_play.png"), 512);

  if (webRoot) {
    await writeFile(join(webRoot, "apps/web/public/logdate-app-icon.svg"), full);
    await writeFile(join(webRoot, "apps/web/public/logdate-app-icon-maskable.svg"), maskable);
    resize(join(temp, "full.png"), join(webRoot, "apps/web/src/app/icon.png"), 512);
    resize(join(temp, "maskable.png"), join(webRoot, "apps/web/public/icon-maskable.png"), 512);
  }
  execFileSync("bash", [join(root, "scripts/regenerate-macos-icon.sh")], { stdio: "inherit" });
} finally {
  await rm(temp, { recursive: true, force: true });
}

console.log(`Regenerated Material icons${webRoot ? " and web install icons" : ""}`);
