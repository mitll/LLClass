#!/usr/bin/python

from latin_normalization import MITLLLatinNormalizer

# Function definition is here
def printme( str ):
   "This prints a passed string into this function"
   print str
   return;

# Now you can call printme function
printme("dude")

lines = [MITLLLatinNormalizer.normalize(line) for line in open('news4L-500each.tsv')]

for line in lines:print line




