/**
 * This file includes polyfills needed by Angular and is loaded before the app.
 * You can add your own extra polyfills to this file.
 *
 * This file is divided into 2 sections:
 *   1. Browser polyfills. These are applied before loading ZoneJS and are sorted by browsers.
 *   2. Application imports. Files imported after ZoneJS that should be loaded before your main
 *      file.
 *
 * The current setup is for so-called "evergreen" browsers; the last versions of browsers that
 * automatically update themselves. This includes recent versions of Safari, Chrome (including
 * Opera), Edge, and Firefox.
 *
 */

/**
 * By default, zone.js will patch all possible macroTask and DomEvents
 * user can disable parts of macroTask/DomEvents patch by setting following flags
 */

// Simplified localStorage polyfill - only implement if needed
// This is for environments where localStorage might not be available
// For Angular apps in modern browsers, this typically isn't necessary
(function() {
  if (typeof window !== 'undefined' && !window.localStorage) {
    // Create a simplified version that doesn't try to modify read-only properties
    const items: Record<string, string> = {};
    const storageSize = () => Object.keys(items).length;
    
    // Define a mock storage API - implementations are simplified
    const mockStorage = {
      getItem(key: string): string | null {
        return key in items ? items[key] : null;
      },
      setItem(key: string, value: string): void {
        items[key] = String(value);
      },
      removeItem(key: string): void {
        delete items[key];
      },
      clear(): void {
        Object.keys(items).forEach(key => delete items[key]);
      },
      key(index: number): string | null {
        return Object.keys(items)[index] || null;
      },
      get length(): number {
        return storageSize();
      }
    };
    
    // Only set if localStorage is undefined
    try {
      Object.defineProperty(window, 'localStorage', {
        value: mockStorage,
        configurable: true,
        enumerable: true,
        writable: false
      });
    } catch (e) {
      // Silently handle errors - polyfill couldn't be applied
    }
  }
})();

/**
 * Zone JS is required by default for Angular itself.
 */
import 'zone.js';  // Included with Angular CLI.

/**
 * Application imports
 */