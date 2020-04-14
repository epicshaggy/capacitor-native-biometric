# Capacitor Native Biometric

## Installation

- `not yet available`

## Usage

```ts
import { Plugins } from "@capacitor/core";

const { NativeBiometric } = Plugins;

NativeBiometric.isAvailable().then(
  (result) => {
    const has = result.has;
    const touchId = result.touchId;
    const faceId = result.faceId;
  },
  (error) => {
    //Couldn't check availability
  }
);

NativeBiometric.verify().then(
  () => {
    //Authentication successful
  },
  (error) => {
    //Failed to authenticate
  }
);
```

... Unfinished
