export interface Acknowledgement {
  name: string
  what: string
  url: string
  license: string
  /** A copy of the licence the app ships with, under `/licenses/`, for those that ask for one. */
  licenseFile?: string
}

export const BUNDLED: Acknowledgement[] = [
  {
    name: 'GeckoView',
    what: 'The engine behind web windows',
    url: 'https://mozilla.github.io/geckoview/',
    license: 'MPL-2.0',
    licenseFile: 'MPL-2.0.txt',
  },
  { name: 'Vue.js', what: 'The system apps’ interface', url: 'https://vuejs.org/', license: 'MIT', licenseFile: 'Vue-MIT.txt' },
  {
    name: 'Papirus icon theme',
    what: 'App and file icons',
    url: 'https://github.com/PapirusDevelopmentTeam/papirus-icon-theme',
    license: 'GPL-3.0',
    licenseFile: 'GPL-3.0.txt',
  },
  {
    name: 'Material Bibata Cursor',
    what: 'Pointer shapes, in the Beige theme',
    url: 'https://github.com/SakibShahariar/material-bibata-cursor',
    license: 'MIT',
    licenseFile: 'Material-Bibata-MIT.txt',
  },
  {
    name: 'Bibata Cursor',
    what: 'The pointer artwork Material Bibata recolours',
    url: 'https://github.com/ful1e5/Bibata_Cursor',
    license: 'GPL-3.0',
    licenseFile: 'GPL-3.0.txt',
  },
  {
    name: 'Material Symbols',
    what: 'Interface icons',
    url: 'https://github.com/google/material-design-icons',
    license: 'Apache-2.0',
    licenseFile: 'Apache-2.0.txt',
  },
  {
    name: 'AndroidX',
    what: 'Core, AppCompat, Activity, Lifecycle and WebKit libraries',
    url: 'https://developer.android.com/jetpack/androidx',
    license: 'Apache-2.0',
    licenseFile: 'Apache-2.0.txt',
  },
  {
    name: 'Material Components for Android',
    what: 'Phone interface components',
    url: 'https://github.com/material-components/material-components-android',
    license: 'Apache-2.0',
    licenseFile: 'Apache-2.0.txt',
  },
  {
    name: 'Kotlin and kotlinx.coroutines',
    what: 'The language and its coroutines',
    url: 'https://kotlinlang.org/',
    license: 'Apache-2.0',
    licenseFile: 'Apache-2.0.txt',
  },
]

export const RESEARCH: Acknowledgement[] = [
  {
    name: 'nrealAirLinuxDriver',
    what: 'Reference driver for the XREAL Air HID protocol',
    url: 'https://github.com/UnrealXR/nrealAirLinuxDriver',
    license: 'MIT',
  },
  {
    name: 'ar-drivers-rs',
    what: 'Reference drivers for XREAL and other AR glasses, in Rust',
    url: 'https://github.com/badicsalex/ar-drivers-rs',
    license: 'MIT',
  },
]
