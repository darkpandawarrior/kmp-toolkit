config.module.rules.push({
    test: /\.mjs$/,
    resolve: { fullySpecified: false },
    type: "javascript/esm",
});
