import guardian from '@guardian/eslint-config';

export default [
    ...guardian.configs.recommended,
    {
        ignores: ["dist/**"],
    },
    {
        // Add Jest globals
        files: ["*.test.js"],
        languageOptions: {
            globals: {
                jest: true,
                describe: true,
                it: true,
                test: true,
                expect: true,
                beforeEach: true,
                afterEach: true,
                beforeAll: true,
                afterAll: true,
            }
        }
    }
];
