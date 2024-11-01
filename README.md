## Usage

```sh
cat putaway-directed-hlf.txt | clj -M -m cfg.core putaway-directed-hlf.jpg
```

## Example

![Putaway Picking - HLF](out/putaway-picking-hlf.jpg)

## TODO

- [X] A lot of processes have a ton of arrows pointing to a sink node. If a node is a sink node (Return), allow it to be replicated in the graph. So then nodes can point to a sink node that's write next to them, rather than having tons of snaky arrows all over the graph
- [ ] now, to expand the above ^... A lot of nodes point to one node, but it's not necessarily a sink. They should maybe also have a goto graph node? But check this - there's a chance this is already a graphviz thing
