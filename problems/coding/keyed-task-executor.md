# Keyed task executor

Serialize tasks by key while bounding total accepted work and parallel execution.

## Solution summary

Enqueue by key and allow only one runner per key. Release global capacity when a task finishes.

[Full solution in interview-prep-raw](https://github.com/redblackcoder/interview-prep-raw/blob/master/code/keyed-task-executor/KeyedTaskExecutor.java)
