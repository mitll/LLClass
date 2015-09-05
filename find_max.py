#!/usr/bin/env python
# file: grep.py
import re, sys, glob, os, fnmatch
from collections import defaultdict
from operator import itemgetter

exp = sys.argv[1]

filelist = os.listdir(".")
print "searching ", len(filelist)

explist = fnmatch.filter(filelist, exp)
print "found ", len(explist)


EXP = defaultdict(float)


for file in explist:
    with open(file) as f:
        for line in f:
            if "accuracy" in line:
                val = line.split(" ")[-1]
                EXP[file] = float(val)
        f.close()


sorted_EXP = sorted(EXP.iteritems(), key=itemgetter(1), reverse=True)
print sorted_EXP[0][0], sorted_EXP[0][1]
