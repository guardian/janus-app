import {
  authenticatePasskey,
  deletePasskey,
  registerPasskey,
} from './passkeys.js';
import { createAndSubmitForm } from './utils/formUtils.js';
import {
  getPasskeyNameFromUser,
  showConfirmationModal,
} from './utils/modalUtils.js';
import { displayToast, messageType } from './utils/toastMessages.js';

// Mock the imported modules
jest.mock('./utils/formUtils.js', () => ({
  createAndSubmitForm: jest.fn(),
}));
jest.mock('./utils/modalUtils.js', () => ({
  getPasskeyNameFromUser: jest.fn(),
  showConfirmationModal: jest.fn(),
}));
jest.mock('./utils/toastMessages.js', () => ({
  displayToast: jest.fn(),
  messageType: {
    error: 'error',
    warning: 'warning',
    info: 'info',
  },
}));

// Mock the fetch API
global.fetch = jest.fn();

describe('Passkeys Module Tests', () => {
  let mockCsrfToken;

  // Setup WebAuthn mocks
  beforeEach(() => {
    mockCsrfToken = 'test-csrf-token';
    jest.clearAllMocks();
    global.PublicKeyCredential = {
      parseRequestOptionsFromJSON: jest.fn().mockReturnValue({}),
      parseCreationOptionsFromJSON: jest.fn().mockReturnValue({}),
    };
    global.navigator.credentials = {
      get: jest.fn().mockResolvedValue({
        toJSON: () => ({ id: 'credential-id', type: 'public-key' }),
      }),
      create: jest.fn().mockResolvedValue({
        toJSON: () => ({ id: 'new-credential-id', type: 'public-key' }),
      }),
    };

    // Mock fetch responses
    fetch.mockImplementation((url) => {
      if (url.includes('auth-options')) {
        return Promise.resolve({
          ok: true,
          json: () =>
            Promise.resolve({
              allowCredentials: [{ id: 'cred1' }],
            }),
        });
      } else if (url.includes('registration-options')) {
        return Promise.resolve({
          ok: true,
          json: () =>
            Promise.resolve({
              authenticatorSelection: { authenticatorAttachment: null },
            }),
        });
      } else {
        return Promise.resolve({
          ok: true,
          json: () => Promise.resolve({ success: true }),
        });
      }
    });

    // Mock utility functions
    createAndSubmitForm.mockImplementation(() => {});
    getPasskeyNameFromUser.mockResolvedValue('Test Passkey');
    showConfirmationModal.mockResolvedValue(true);
    displayToast.mockImplementation(() => {});
  });

  describe('registerPasskey', () => {
    test('should complete registration flow when user has existing passkeys', async () => {
      await registerPasskey(mockCsrfToken);
      expect(fetch).toHaveBeenCalledWith(
        '/passkey/registration-auth-options',
        expect.anything(),
      );
      expect(showConfirmationModal).toHaveBeenCalledWith(
        'Authenticate with existing passkey',
        expect.any(String),
        'authenticate',
      );
      expect(navigator.credentials.get).toHaveBeenCalled();
      expect(fetch).toHaveBeenCalledWith(
        '/passkey/registration-options',
        expect.anything(),
      );
      expect(navigator.credentials.create).toHaveBeenCalled();
      expect(getPasskeyNameFromUser).toHaveBeenCalled();
      expect(createAndSubmitForm).toHaveBeenCalledWith(
        '/passkey/register',
        expect.objectContaining({
          csrfToken: mockCsrfToken,
          passkeyName: 'Test Passkey',
        }),
      );
    });

    test('should handle error scenarios', async () => {
      navigator.credentials.create.mockRejectedValue(new Error('Test error'));
      await registerPasskey(mockCsrfToken);
      expect(displayToast).toHaveBeenCalledWith(
        'Passkey registration failed',
        messageType.error,
      );
    });
  });

  describe('authenticatePasskey', () => {
    test('should authenticate and submit form when user has a passkey', async () => {
      await authenticatePasskey('/target-url', mockCsrfToken);
      expect(fetch).toHaveBeenCalledWith(
        '/passkey/auth-options',
        expect.anything(),
      );
      expect(navigator.credentials.get).toHaveBeenCalled();
      expect(createAndSubmitForm).toHaveBeenCalledWith(
        '/target-url',
        expect.objectContaining({
          csrfToken: mockCsrfToken,
        }),
      );
    });
  });

  describe('deletePasskey', () => {
    test('should delete passkey after authentication', async () => {
      global.fetch
        .mockImplementationOnce(() =>
          Promise.resolve({
            ok: true,
            json: () =>
              Promise.resolve({ allowCredentials: [{ id: 'cred1' }] }),
          }),
        )
        .mockImplementationOnce(() =>
          Promise.resolve({
            ok: true,
            json: () => Promise.resolve({ success: true }),
          }),
        );
      const result = await deletePasskey('passkey-id-123', mockCsrfToken);
      expect(fetch).toHaveBeenCalledWith(
        '/passkey/auth-options',
        expect.anything(),
      );
      expect(navigator.credentials.get).toHaveBeenCalled();
      expect(fetch).toHaveBeenCalledWith(
        '/passkey/passkey-id-123',
        expect.objectContaining({
          method: 'DELETE',
          headers: {'Content-Type': 'text/plain', 'CSRF-Token': mockCsrfToken},
        }),
      );
      expect(result).toEqual({ success: true });
    });
  });
});
