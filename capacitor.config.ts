import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'com.iris.notes',
  appName: 'Iris Notes',
  webDir: 'dist',
  plugins: {
    Keyboard: {
      // Shrink the web view so the fixed formatting toolbar sits above the keyboard
      resize: 'native',
      // Hide the prev/next/done accessory bar that covered the toolbar
      accessoryBarVisible: false,
    },
  },
};

export default config;
