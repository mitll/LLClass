#!/usr/bin/env python

# text_normalization.py
#
# Generic Text Normalization Routines
# 
# Copyright 2013-2016 Massachusetts Institute of Technology, Lincoln Laboratory
# version 0.1
#
# author: Charlie Dagli & William M. Cambpell
# {dagli,wcampbell}@ll.mit.edu
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

import re
import logging
import unicodedata

class MITLLTextNormalizer: 
    """ Text-Normalization Routines """

    # Logging
    LOG_LEVEL = logging.INFO
    logging.basicConfig(level=LOG_LEVEL,
                                format='%(asctime)s %(levelname)-8s %(message)s',
                                                    datefmt='%a, %d %b %Y %H:%M:%S')
    logger = logging.getLogger(__name__)
    

    def __init__(self):
        """ Constructor """
        self.rewrite_hash = self.create_utf8_rewrite_hash()


    def normalize(self,ln):
        """ Text Line Normalizer """
        # Various normalization routines -- pick and choose as needed
        ln = unicode(ln) #make sure we're in unicode
        ln = self.normalize_unicode_composed(ln) #from base-class
        ln = self.filter_unicode(ln) #from base-class
        ln = self.remove_html_markup(ln) #from base class
        if (ln == ' '):
            ln = ''
        return ln


    def normalize_unicode_composed(self,txt):
        """ Normalize unicode: Composed """
        return unicodedata.normalize('NFKC', txt)


    def normalize_unicode_decomposed(self,txt):
        """ Normalize unicode: Decomposed (i.e. expanded unicode) """
        return unicodedata.normalize('NFKD', txt)


    def filter_unicode(self,ln):
        """ Filter Unicode """
        out = ''
        for i in xrange(0,len(ln)):
            if (ord(ln[i]) < 0x7f):
                out = out + ln[i]
            elif (self.rewrite_hash.has_key(ln[i])):
                out = out + self.rewrite_hash[ln[i]]
            else:
                out = out + " "

        # Clean up extra spaces
        out = re.sub('^\s+', '', out)
        out = re.sub('\s+$', '', out)
        out = re.sub('\s+', ' ', out)
        out = re.sub('\s+.$', '.', out)
        
        return out


    def create_utf8_rewrite_hash (self):
        """ 
        Rewrite utf-8 chars to ascii in a rational manner
            Strictly speaking (and in python) 
            any ascii character >= 128 is not valid
        """

        rewrite_hash = dict([])
        rewrite_hash[u'\xA0'] = " "       # NO-BREAK SPACE 
        rewrite_hash[u'\xA6'] = " "       # BROKEN BAR
        rewrite_hash[u'\xA7'] = " "       # SECTION SIGN
        rewrite_hash[u'\xAC'] = " "       # NOT SIGN
        rewrite_hash[u'\xAD'] = " "       # SOFT HYPHEN

        rewrite_hash[u'\xB6'] = " "       # PILCROW SIGN
        rewrite_hash[u'\xBC'] = " 1/4 "   # VULGAR FRACTION ONE QUARTER
        rewrite_hash[u'\xBD'] = " 1/2 "   # VULGAR FRACTION ONE HALF
        rewrite_hash[u'\xBE'] = " 3/4 "   # VULGAR FRACTION THREE QUARTERS

        rewrite_hash[u'\u0336'] = " "     # COMBINING LONG STROKE OVERLAY

        rewrite_hash[u'\u2000'] = " "     # EN QUAD
        rewrite_hash[u'\u2001'] = " "     # EM QUAD
        rewrite_hash[u'\u2009'] = " "     # THIN SPACE
        rewrite_hash[u'\u200A'] = " "     # HAIR SPACE
        rewrite_hash[u'\u200B'] = " "     # ZERO WIDTH SPACE

        rewrite_hash[u'\u200E'] = " "     # LEFT-TO-RIGHT MARK
        rewrite_hash[u'\u200F'] = " "     # RIGHT-TO-LEFT MARK

        rewrite_hash[u'\u2010'] = "-"     # HYPHEN
        rewrite_hash[u'\u2011'] = "-"     # NON-BREAKING HYPHEN
        rewrite_hash[u'\u2013'] = " "     # EN DASH
        rewrite_hash[u'\u2014'] = " "     # EM DASH
        rewrite_hash[u'\u2015'] = " "     # HORIZONTAL BAR

        rewrite_hash[u'\u2020'] = " "     # DAGGER
        rewrite_hash[u'\u2021'] = " "     # DOUBLE DAGGER
        rewrite_hash[u'\u2022'] = " "     # BULLET
        rewrite_hash[u'\u2023'] = " "     # TRIANGULAR BULLET
        rewrite_hash[u'\u2024'] = " "     # ONE DOT LEADER
        rewrite_hash[u'\u2025'] = " "     # TWO DOT LEADER
        rewrite_hash[u'\u2026'] = " "     # HORIZONTAL ELLIPSIS
        rewrite_hash[u'\u2027'] = " "     # HYPHENATION POINT
        rewrite_hash[u'\u2028'] = " "     # LINE SEPARATOR
        rewrite_hash[u'\u2029'] = "\n"    # PARAGRAPH SEPARATOR
        rewrite_hash[u'\u202A'] = " "     # LEFT-TO-RIGHT EMBEDDING (???)
        rewrite_hash[u'\u202B'] = " "     # RIGHT-TO-LEFT EMBEDDING (???)
        rewrite_hash[u'\u202C'] = " "     # POP DIRECTIONAL FORMATTING (???)
        rewrite_hash[u'\u202D'] = " "     # LEFT-TO-RIGHT OVERRIDE
        rewrite_hash[u'\u202E'] = " "     # RIGHT-TO-LEFT OVERRIDE
        rewrite_hash[u'\u202F'] = " "     # NARROW NO-BREAK SPACE

        rewrite_hash[u'\u203B'] = " "     # REFERENCE MARK

        rewrite_hash[u'\u206B'] = " "     # ACTIVATE SYMMETRIC SWAPPING
        rewrite_hash[u'\u206E'] = " "     # NATIONAL DIGIT SHAPES
        rewrite_hash[u'\u206F'] = " "     # NOMINAL DIGIT SHAPES

        rewrite_hash[u'\u2116'] = " "     # NUMERO SIGN
        rewrite_hash[u'\u2154'] = "2/3"   # VULGAR FRACTION TWO THIRDS
        rewrite_hash[u'\u2192'] = " "     # RIGHTWARDS ARROW
        rewrite_hash[u'\u21FC'] = " "     # LEFT RIGHT ARROW WITH DOUBLE VERTICAL STROKE

        rewrite_hash[u'\u2212'] = "-"     # MINUS SIGN

        rewrite_hash[u'\u23AF'] = " "     # HORIZONTAL LINE EXTENSION   
        rewrite_hash[u'\u25BA'] = " "     # BLACK RIGHT-POINTING POINTER
        rewrite_hash[u'\u2665'] = " "     # BLACK HEART SUIT

        return rewrite_hash


    def remove_html_markup (self,ln):
        """ remove HTML style angle bracketed tags """

        ln = re.sub('\<\S+\>', ' ', ln)
        
        # remove market symbols
        # ln = re.sub('\([A-Z0-9a-z\_]*\.[A-Z]+\:[^\)]+\)\,?', '', ln)

        # remove web site URLs and links
        ln = re.sub('https?:\/\/?\s*\S+\s', ' ', ln)
        ln = re.sub('https?:\/\/?\s*\S+$', '', ln)
        ln = re.sub('\(https?:\\\\\S+\)', ' ', ln)
        ln = re.sub('\(?www\.\S+\)?', ' ', ln)
        ln = re.sub('\[ID:[^\]]+\]', ' ', ln)
        ln = re.sub('\[id:[^\]]+\]', ' ', ln)
        ln = re.sub('\(PDF\)', ' ', ln)
        
        # replace html special characters
        ln = re.sub(r'&mdash;', ' ', ln)
        ln = re.sub(r'\&quot\;', ' ', ln)
        ln = re.sub(r'\&\#39\;', ' ', ln)

        # Clean up extra spaces
        ln = re.sub('^\s+', '', ln)
        ln = re.sub('\s+$', '', ln)
        ln = re.sub('\s+', ' ', ln)

        return ln


    def clean_string(self,s):
        """ Strip leading characters, lower """

        if isinstance(s,unicode):
            ss = s.lower()
        else:
            ss = unicode(s.lower(),"utf-8")

        if len(ss) == 0:
            ss = u''

        return ss

