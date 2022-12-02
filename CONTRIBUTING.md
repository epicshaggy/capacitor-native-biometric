# Contributing

## Submitting a Pull Request (PR)

Before you submit your Pull Request (PR) consider the following guidelines:

1. Search GitHub for an open or closed PR that relates to your submission. You don't want to duplicate existing efforts.

2. Be sure that an issue describes the problem you're fixing, or documents the design for the feature you'd like to add. Discussing the design upfront helps to ensure that we're ready to accept your work.

3. Fork the repo.

4. In your forked repository, make your changes in a new git branch:
    - `git checkout -b branch-name-foo`

5. Npm install dependencies
    - `npm ci` (clean install)

6. Symlink repo as `capacitor-native-biometric` package to your capacitor application for easy development via [`npm link`](https://docs.npmjs.com/cli/v8/commands/npm-link)
    1. In `capacitor-native-biometric` repo root, where package.json is, run `npm link`

    2. In the root of your capacitor application that uses `capacitor-native-biometric`, where package.json is, run `npm link capacitor-native-biometric`

7. Make and commit changes
    - `git commit --all`

8. Push your branch to GitHub:
    - `git push origin branch-name-foo`

9. In GitHub, send a pull request to `capacitor-native-biometric`
