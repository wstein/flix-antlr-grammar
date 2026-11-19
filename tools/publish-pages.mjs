#!/usr/bin/env node
/**
 * Add the packed npm tarball of this repository to a GitHub Pages checkout and refresh the index.
 *
 *   node tools/publish-pages.mjs <pages-dir> [<tarball>]
 *
 * Without <tarball> the script runs `npm pack` into a temporary directory. Versions are immutable: an
 * existing version with different bytes is an error, and re-adding identical bytes is a no-op.
 * Writes <pages-dir>/index.json (all versions, with integrity) and <pages-dir>/latest.json.
 */
import { execFileSync } from "node:child_process";
import { createHash } from "node:crypto";
import { copyFileSync, existsSync, mkdirSync, mkdtempSync, readFileSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { basename, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const root = resolve(fileURLToPath(import.meta.url), "..", "..");
const pagesDir = resolve(process.argv[2] ?? (() => { throw new Error("usage: publish-pages.mjs <pages-dir> [tarball]"); })());
mkdirSync(pagesDir, { recursive: true });

const pkg = JSON.parse(readFileSync(join(root, "package.json"), "utf8"));
let tarball = process.argv[3] && resolve(process.argv[3]);
if (!tarball) {
    // Never pack into the Pages directory: npm would overwrite an already published version.
    const tmp = mkdtempSync(join(tmpdir(), "flix-grammar-pack-"));
    const name = execFileSync("npm", ["pack", "--silent", "--pack-destination", tmp], { cwd: root, encoding: "utf8" }).trim().split("\n").pop();
    tarball = join(tmp, name);
}
const file = basename(tarball);
const bytes = readFileSync(tarball);
const integrity = "sha512-" + createHash("sha512").update(bytes).digest("base64");

const target = join(pagesDir, file);
if (resolve(target) !== resolve(tarball)) {
    if (existsSync(target) && !readFileSync(target).equals(bytes)) {
        throw new Error(`${file} is already published with different content; versions are immutable`);
    }
    copyFileSync(tarball, target);
}

const indexPath = join(pagesDir, "index.json");
const index = existsSync(indexPath) ? JSON.parse(readFileSync(indexPath, "utf8")) : { name: pkg.name, versions: {} };
const previous = index.versions[pkg.version];
if (previous && previous.integrity !== integrity) {
    throw new Error(`index.json already lists ${pkg.version} with a different integrity; versions are immutable`);
}
index.versions[pkg.version] = { tarball: file, integrity };

const key = (v) => v.split(/[.-]/).map((p) => (/^\d+$/.test(p) ? Number(p) : p));
const newer = (a, b) => {
    const [x, y] = [key(a), key(b)];
    for (let i = 0; i < Math.max(x.length, y.length); i++) {
        if (x[i] === y[i]) continue;
        return typeof x[i] === "number" && typeof y[i] === "number" ? x[i] - y[i] : String(x[i] ?? "").localeCompare(String(y[i] ?? ""));
    }
    return 0;
};
const versions = Object.keys(index.versions).sort(newer);
index.versions = Object.fromEntries(versions.map((v) => [v, index.versions[v]]));
index.latest = versions[versions.length - 1];

writeFileSync(indexPath, JSON.stringify(index, null, 2) + "\n");
writeFileSync(join(pagesDir, "latest.json"), JSON.stringify({ name: pkg.name, version: index.latest, ...index.versions[index.latest] }, null, 2) + "\n");
console.log(`published ${file} (${integrity.slice(0, 19)}…); latest is ${index.latest}`);
