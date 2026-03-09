/**
 * Minimal mock for react-native used by bridge tests.
 *
 * NativeModules is a shared mutable object — tests can set properties
 * on it before requiring bridge modules that cache moduleRef at load time.
 */

// Use a shared object that persists across jest.resetModules()
if (!global.__RN_MOCK_NATIVE_MODULES__) {
  global.__RN_MOCK_NATIVE_MODULES__ = {};
}

if (!global.__RN_MOCK_PLATFORM__) {
  global.__RN_MOCK_PLATFORM__ = { OS: 'android', select: (obj) => obj.android || obj.default };
}

const NativeModules = global.__RN_MOCK_NATIVE_MODULES__;
const Platform = global.__RN_MOCK_PLATFORM__;

class NativeEventEmitter {
  constructor() {}
  addListener(event, callback) {
    return { remove: jest.fn() };
  }
  removeAllListeners() {}
}

module.exports = {
  NativeModules,
  NativeEventEmitter,
  Platform,
};
