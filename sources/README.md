# Source collection

| Collection | Contents |
|---|---|
| [Code](code/) | Saved implementations, project build files, examples and available tests. |
| [Whiteboards](whiteboards/) | Anonymized diagrams and exports. |
| [Coding notes](notes/coding/) | Recorded work where an exercise was captured in notes. |
| [Design notes](notes/designs/) | Session notes and pointers to the underlying boards. The [reservation board](notes/restaurant-reservation-whiteboard.md) contains the original SQL sketch. |
| [References](references/) | Study guides, papers and reference material. |

## Migration inventory

The source snapshot came from the raw repository at commit `182c6bd`. Its code projects retain their internal directory layout. Exercise notes moved from code into notes/coding, design notes into notes/designs, diagrams into whiteboards, and standalone documents into references. The pilot's executor and reservation artifacts were reused. The crawler drawing uses its anonymized whiteboard export.

The former wiki source extracts now appear within the relevant problem's retrospective sections or in [reading notes](../wiki/reading-notes/README.md). Project history, vault configuration and the replay patch that duplicated identifying fixtures were excluded. HTTP replay fixtures use synthetic identifiers and payloads; their method sequence, request dependencies and expected status codes remain available for study.
