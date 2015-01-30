#!/usr/bin/python3

# Requirements:
# pip3 install git+git://github.com/nlhepler/pydot 
# pip3 install git+git://github.com/pygraphviz/pygraphviz.git
# sudo apt-get install python3-pygraph


import re
import pygraph.readwrite.dot
import sys
import getopt

from pygraph.classes.digraph import digraph
from collections import deque
from pyparsing import Word, Literal, Forward,\
                      ZeroOrMore, Optional, Suppress,\
                      alphas, nums, alphanums, nestedExpr

dirs = []

def coroutine(func):
    def start(*args, **kwargs):
        g = func(*args, **kwargs)
        g.next()
        return g
    return start


@coroutine
def cotuple2list():
    result = None
    while True:
        (tup, co_pool) = (yield result)
        result = list(tup)
        # I don't like using append. So I am changing the data in place.
        for (i,x) in enumerate(result):
            # consider using "if hasattr(x,'__iter__')"
            if isinstance(x,tuple):
                result[i] = co_pool[0].send((x, co_pool[1:]))


@coroutine
def colist2tuple():
    result = None
    while True:
        (lst, co_pool) = (yield result)
        # I don't like using append so I am changing the data in place...
        for (i,x) in enumerate(lst):
            # consider using "if hasattr(x,'__iter__')"
            if isinstance(x,list):
                lst[i] = co_pool[0].send((x, co_pool[1:]))
        result = tuple(lst)


def list2tuple(a):
    return tuple((list2tuple(x) if isinstance(x, list) else x for x in a))
def tuple2list(a):
    return list((tuple2list(x) if isinstance(x, tuple) else x for x in a))


def lineGen(path):
  with open(path, 'r+') as f:
    for line in f:
      yield line.strip()


def printDot(path, startNode):
  dotFile = open(path, 'r')
  content = dotFile.read()

  graph = pygraph.readwrite.dot.read(content)
  return printGraph(graph, startNode)


def printGraph(graph, startNode):
  queue = deque()
  queue.append(startNode)
  queue.append(None)
  
  while(True):
    nxt = queue.popleft()
    if nxt == None:
      print()
      if len(queue) == 0: break
      else: queue.append(None)
    else:
      sys.stdout.write(str(nxt) + ' ')
      neighs = graph.incidents(nxt)
      for n in neighs: queue.append(n)

  return graph


def parseTypes(path):
  msgs = set()
  types = {}

  for line in lineGen(path):
    number = Word(nums)
    word = Word(alphanums + "-_")

    wordList = Forward()
    wordList = word + ZeroOrMore(',' + word)

    par = (Literal('NetworkPartition').setResultsName('type') +\
      '(' + Literal('Set') + '(' +\
      wordList.setResultsName('p1') + ')' + ',' +\
      Literal('Set') + '(' +\
      wordList.setResultsName('p2') + ')' + \
      ')')

    subType = (word + Optional(nestedExpr('(', ')'))).setResultsName('msg')
    msg = (Literal('MsgEvent').setResultsName('type') +\
       '(' + word.setResultsName('src') + ',' +\
       word.setResultsName('dst') + ',' +\
       subType  + ')')

    event = Word( nums ) +\
      Literal('Unique') + "(" + (msg | par) + ',' +\
      number.setResultsName('uid')  + ')'

    result = event.parseString(line)

    key = result.uid
    if result.type == 'MsgEvent':
      msg = list2tuple( result.msg.asList() )
      value = (result.type, result.src, result.dst, msg)
      msgs.add(msg)
    elif result.type == 'NetworkPartition':
      value = (result.type, result.p1, result.p2)

    types[key] = value

  return types




def main(argv):

  try:
    opts, args = getopt.getopt(argv, '')
  except getopt.GetoptError:
    print('test.py <dir> <dir>')
    sys.exit(2)

  assert (len(args) == 2)
  dirs = args
  graphs = [
    printDot(dirs[0] + '/graph.txt', '0'),
    printDot(dirs[1] + '/graph.txt', '0')
  ]
  
  types = [
    parseTypes(dirs[0] + '/types.txt'),
    parseTypes(dirs[1] + '/types.txt')
  ]


  newG = digraph()
  startN = ('0', '0')
  newG.add_node(startN)
  oneToOne(newG, graphs, types, startN)
  printGraph(newG, ('0', '0'))




def oneToOne(newG, graphs, types, same):
  (nodeA, nodeB) = same
  neighs = [
    graphs[0].incidents(nodeA),
    graphs[1].incidents(nodeB)
  ]
  
  mapping = [{}, {}]
  queue = deque()

  for node in neighs[0]:
    t = types[0][node]
    queue.append((node, t))
    mapping[0][t] = node
  
  for node in neighs[1]:
    t = types[1][node]
    mapping[1][t] = node

  parent = (nodeA, nodeB)

  nxt = []
  while(len(queue) > 0):
    (uid, t) = queue.pop()
    if t in mapping[1]:
      newN = (uid, mapping[1][t])
      nxt.append(newN)
      newG.add_node(newN)
      newG.add_edge((newN, parent))

  for (a, b) in nxt:
    oneToOne(newG, graphs, types, (a, b))
      

if __name__ == "__main__":
  main(sys.argv[1:])
