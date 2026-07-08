#!/usr/bin/env node
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const pluginDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");

const requiredKeys = [
  "isAvailable",
  "authenticationStrength",
  "biometryType",
  "deviceIsSecure",
  "strongBiometryIsAvailable",
];

function read(rel) {
  return fs.readFileSync(path.join(pluginDir, rel), "utf8");
}

const errors = [];

const android = read("android/src/main/java/ee/forgr/biometric/NativeBiometric.java");
const androidBlock = android.match(/private JSObject checkBiometryAvailability\([\s\S]*?^    \}/m);
if (!androidBlock) {
  errors.push("Android: missing checkBiometryAvailability()");
} else {
  for (const key of requiredKeys) {
    if (!androidBlock[0].includes(`put("${key}"`)) {
      errors.push(`Android: checkBiometryAvailability() missing ret.put("${key}")`);
    }
  }
}

const ios = read("ios/Sources/NativeBiometricPlugin/NativeBiometricPlugin.swift");
const iosBlock = ios.match(/private func checkBiometryAvailability\([\s\S]*?^    \}/m);
if (!iosBlock) {
  errors.push("iOS: missing checkBiometryAvailability()");
} else {
  for (const key of requiredKeys) {
    if (!iosBlock[0].includes(`obj["${key}"]`)) {
      errors.push(`iOS: checkBiometryAvailability() missing obj["${key}"]`);
    }
  }
}

const defs = read("src/definitions.ts");
const available = defs.match(/export interface AvailableResult \{[\s\S]*?\n\}/);
if (!available || !available[0].includes("biometryType:")) {
  errors.push("TypeScript: AvailableResult missing biometryType");
}

if (errors.length) {
  console.error("[is-available] FAIL");
  for (const e of errors) console.error(`- ${e}`);
  process.exit(1);
}

console.log("[is-available] OK: isAvailable() native result includes biometryType and related fields");
