# 2. Existing tools

Several tools already help students see what their code does. This chapter describes the four most relevant, as of November 2026, and where Raphael differs from each.

## 2.1 JavaWiz
JavaWiz is an educational visual debugger for Java from the Institute for System Software at Johannes Kepler University Linz. It was presented at ICPC 2025 and won teaching awards at JKU. It is open source under GPL-3.0, and version 2.4.0 was current when this was written.

JavaWiz records the whole run, so the user can move backwards as well as forwards. It shows ten synchronised views:
- a flowchart of the code
- a memory diagram (stack, statics, heap)
- a trace table of values per step
- array, list and tree views, which animate assignments and insertions
- a UML sequence diagram of method calls
- an input view showing what has been read
- a view of Java streams
- a searchable history
- a console

It is installed as a VS Code extension or an IntelliJ plugin. It needs JDK 25 or newer, with `java` and `javac` from the same JDK. Its backend is written in Kotlin and its frontend in Vue, and its memory view is laid out by the d3-graphviz library.

## 2.2 Python Tutor
Python Tutor is a website that visualises Python, Java, C, C++ and JavaScript, and is very widely used. It draws the stack and the heap as boxes and arrows. Runs are capped at 1,000 steps (about 300 for C and C++), and its own guidance is that code longer than a slide is too long to visualise well. Every structure is drawn the same generic way, so a binary tree looks like any other object graph. The code is run on its server.

## 2.3 jGRASP
jGRASP, from Auburn University, is a long-standing Java IDE whose data structure viewers recognise stacks, queues, linked lists, binary trees and hash tables, and draw them in a textbook style, animating nodes being added and removed. The viewers are opened by hand on an object during debugging, the debugger steps forwards only, and the user must work inside jGRASP's own IDE.

## 2.4 VisuAlgo
VisuAlgo, from the National University of Singapore, has polished animations of sorting, trees, graphs and many other algorithms. It animates its own built-in implementations, not the student's code.

## 2.5 Comparison

| | Raphael | JavaWiz | Python Tutor | jGRASP | VisuAlgo |
|---|---|---|---|---|---|
| Runs the user's own code | Yes | Yes | Yes | Yes | Mostly no |
| Setup | Install one app | IDE + plugin + JDK 25 | None (website) | Install IDE | None (website) |
| Works offline | Yes | Yes | No | Yes | No |
| Step backwards | Yes | Yes | Yes | No | Yes |
| Textbook layout of lists and trees | Yes | Yes | No | Yes | Yes |
| B-trees, grids, tries | Yes | No | No | No | Own demos only |
| Index markers and read/write outlines | Yes | Animated index expressions | No | No | Own demos only |
| Animation | Springs: arrows swing, values fly | Some views | No | Some | Yes |
| Play with speed control | Yes | Not stated | No | Not checked | Yes |
| Step limit | 2,000, with live stop | Not stated | 1,000 | None stated | — |
| Memory diagram, trace table, flowchart | Planned (ROADMAP stage 9) | Yes | Memory only | No | No |

## 2.6 Raphael's position
Raphael's aim is to be the easiest way to see your own Java program working:
- paste any program and press run, with no IDE and nothing else to install;
- get a picture that looks like the textbook's, including B-trees and DP grids;
- see every change animated, with markers that show what the next line will do;
- get a plain explanation when something goes wrong.

From JavaWiz, the closest tool, Raphael plans to take the views it lacks (ROADMAP stage 9) and link them all, so that clicking anything in any view jumps to that moment in the run.

## Sources
- JavaWiz: [GitHub repository and README](https://github.com/SSW-JKU/javawiz), [javawiz.net](https://javawiz.net/), [changelog](https://javawiz.net/changelog.html), [JKU research page (ICPC 2025)](https://research.jku.at/en/publications/javawiz-a-trace-based-graphical-debugger-for-software-development/), [JKU news release](https://www.jku.at/institut-fuer-volkswirtschaftslehre/news-events/detail/news/javawiz-neues-tool-der-jku-erleichtert-einstieg-in-den-programmier-unterricht/)
- Python Tutor: [visualizer](https://PythonTutor.com/visualize.html), [llms.txt (limits)](https://pythontutor.com/llms.txt), [Java for AP CS A](https://pythontutor.com/articles/ap-computer-science-java.html)
- jGRASP: [jgrasp.org](https://www.jgrasp.org), [viewers tutorial](https://jgrasp.org/tutorials187/10_Viewers.pdf), [SIGCSE 2007 paper](https://www.JGrasp.org/papers/sigcse2007paper.fp204-cross.pdf)
- VisuAlgo: [visualgo.net](https://visualgo.net/)
