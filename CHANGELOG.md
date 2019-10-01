# 1.0.4
## bugfixes
- When opening a buffer by clicking, process-filter would accidently erase the contents of the org buffer opened and not the process buffer

# 1.0.3
## bugfixes
- in emacs: warn when org analyzer is started with an org-directory pointing nowhere but don't fail to start so that the org-directory can be set inside the app
- also deploy jar again that now hopefully has the shift-click fix

# 1.0.2
## bugfixes
- fix 24 hour strings

## features
- show error message when fetch clocks fails — kinda scary looking, might need to improve UX on that.

# 1.0.1

## bugfixes
- fix holding shift key (add to selection) and alt key (remove from selection)
  while clicking / selecting in the calendar
- don't include backup files

# 1.0.0

## features
- bar chart showing time clocked per day
- print / export
- group clocks by day / week / month
- select org files from within app

# 0.3.5

## bugfixes
- fixing localized CLOCKs [#3](https://github.com/rksm/clj-org-analyzer/issues/3)

# 0.3.4

## feature
- allow to trigger selecting org files from the file info view

## bugfixes
- render org-links in the heading, fixes [#8](https://github.com/rksm/clj-org-analyzer/issues/8)
- recognize bold text, fixes [#10](https://github.com/rksm/clj-org-analyzer/issues/10)
- properly parse sections with non-linear depth, fixes [#9](https://github.com/rksm/clj-org-analyzer/issues/9)

# 0.3.3
## bugfixes
- fix server shutdown issue

# 0.3.2
## bugfixes
- Fix regexp to match non-english timestamps (Thanks @hso!)

# 0.3.1
## minor changes
- toggle between selecting all days and none with ctrl-a
- include .org_archive files

# 0.3.0
## bugfixes
- Fixed finding CLOCKs with whitespace prefix.
