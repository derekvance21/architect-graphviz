- [ ] fix CLI API
    - default is `[FILE] ...` - this will create output to stdout
    - then, `-i` - in-place - this will create output at same place as input, but with output file extension added
        - if -T is svg, then output with `.html` file extension
    - `-d` - directory - set the directory where output files will go
    - `-o` - set output file where output will go
    - `-T` - set output format type
    - `-X` - set output file extension?
    - `-I` - set input format type - *diff* format is the other one; default is *source* format
- [ ] output file extension should be `.html` - this lets VSCode live preview linking work correctly
- [ ] non-connected (unreachable code) nodes should be removed
- [ ] merge simple block calculate, send and list nodes
- [ ] edge links to their destination
- [ ] refactor so that the program is - read the input, put it into an ubergraph. Then, apply a serious of transformations to the graph (merge basic clock, merge edges, set labels, color edges, always-pass-calculates, etc., etc.). And then if you're really crazy, you'd let the user pick the transformations
- [ ] `architect-source-code` should have different extensions for different output types. Business and Process Objects have the same output format, but they're different from db/compare/calculate, so their file extensions should reflect that. This'll allow the user to do `clj -M -m cfg.core -i WA/*.pob` in one go.
- [ ] if something points a node that is part of a basic block into a sink (return), then create those extra nodes for it, to prevent tons of arrows going to Calculate - Log: Data Error
- [ ] omg - you know what's a good idea? So basically, how do you represent switch statements. So a string of compare actions, where each fails into the other, and succeeds into something else. So you can merge those into one node. So it's implied that going down through the node means you failed, and succeeding brings you out of the switch.
F1? -> do "cancel" stuff
F2? -> do "switch" stuff
F3? -> do "done" stuff
 But then it's like, usually there's a "default", and that usually points to invalid option. So the last one succeeds as valid input, the last one fails as invalid option. And it has to be a requirement that there's no other way into the compare chain. There's one way in, lots of ways out. And the compares are all linked by failure edges. And I guess it could also be linked by pass edges, but that'd be something different. That's almost like a series of try catch blocks, or the golang pattern of `if err != nil`, and then handle that. This is honestly more useful for a series of call blocks, linked by pass edges. That's super common lowkey now that I think about it. Call - LP, Call - Item, Call - Lot, Call Quantity, Call TX. But usually, it's not a basic block, they point to each other a lot. And that describes something useful that it makes sense to combine - to pass out of this block, you need to pass at all the following. That's what it's saying. And if you fail at them, then you'll point all over the place. But maybe you make that a cluster instead of a combined node. Or just subgraphs! Quote:
 > Subgraphs play three roles in Graphviz. First, a subgraph can be used to represent graph structure, indicating that certain nodes and edges should be grouped together. This is the usual role for subgraphs and typically specifies semantic information about the graph components.
And you'd use rank=same; to make sure they line up together, I think
I'm realizing that subgraphs are cool. With the `rank` attribute, you can ensure that nodes line up together, ya know? Actually, you can't ensure that subgraph nodes end up aligned vertically without some shenanigans, like adding `weight` to edges, where:
> the heavier the weight, the shorter, straighter and more vertical the edge is
Unfortunately, I don't think ubergraph supports subgraphs... I'd have to see if dorothy does, and then do some manually stuff to get it to work
