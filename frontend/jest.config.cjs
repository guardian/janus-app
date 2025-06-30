module.exports = {
    transform: {
        '^.+\\.js$': 'babel-jest',
    },
    testEnvironment: 'jsdom',
    moduleFileExtensions: ['js'],
    transformIgnorePatterns: ['/node_modules/']
};
