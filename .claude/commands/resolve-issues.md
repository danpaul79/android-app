Pull down all open GitHub issues for this repo, then plan, design, and implement fixes for each one. Follow this workflow strictly:

## Step 1: Fetch Open Issues
Run `gh issue list --state open --json number,title,body,labels,assignees` to get all open issues. Display a summary of each issue.

## Step 2: Plan & Design
For each issue (or group of related issues):
1. Analyze the issue requirements and acceptance criteria
2. Identify which files need to change
3. Design the solution — consider architecture, data model changes, UI changes, and edge cases
4. Present the full plan before writing any code

## Step 3: Implement
For each issue, write the code to meet the requirements:
- Follow existing patterns in the codebase (manual DI via AppContainer, Compose Navigation, Room DB, etc.)
- Run `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :app:compileDebugKotlin` to verify the build compiles after changes
- Fix any compilation errors before moving on

## Step 4: Commit & Push
After all issues are resolved:
1. Stage the relevant files
2. Create a commit with a descriptive message referencing the issue numbers (e.g., "Fix #1, Fix #2: description")
   - Using "Fix #N" in the commit message will auto-close the issues on push
3. Push to main: `git push`

## Important Notes
- Always read existing code before modifying it
- Run the compiler after each significant change
- If an issue is unclear or seems too large, break it into smaller steps
- Reference CLAUDE.md for architecture and conventions
- Each issue fix should be a logical unit — group related issues if they touch the same code
- Update CLAUDE.md with relavent changes and changes to overall vision.
