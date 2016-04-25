#!/usr/bin/env python

# latin_normalization.py
#
# Text Normalization Routines for Latin Script Text (including for Twitter data)
# 
# Copyright 2013-2016 Massachusetts Institute of Technology, Lincoln Laboratory
# version 0.1
#
# author: William M. Campbell and Charlie Dagli
# {wcampbell,dagli}@ll.mit.edu
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

from .text_normalization import MITLLTextNormalizer

class MITLLLatinNormalizer(MITLLTextNormalizer): 
    """
    Text-Normalization Routines for Latin Script Text
    """

    # Logging
    LOG_LEVEL = logging.INFO
    logging.basicConfig(level=LOG_LEVEL,
                                format='%(asctime)s %(levelname)-8s %(message)s',
                                                    datefmt='%a, %d %b %Y %H:%M:%S')
    logger = logging.getLogger(__name__)
    

    def __init__(self):
        """ Constructor """
        MITLLTextNormalizer.__init__(self)
        self.update_utf8_rewrite_hash()


    def normalize(self,ln):
        """ Normalize text """
        ln = MITLLTextNormalizer.normalize(self,ln)
        ln = self.convertUTF8_to_ascii(ln)
        ln = self.remove_twitter_meta(ln)
        ln = self.remove_nonsentential_punctuation(ln)
        ln = self.remove_word_punctuation(ln)
        ln = self.remove_repeats(ln)
        if (ln == ' '):
            ln = ''
        return ln


    def get_counts (self,msg):
        """ Word Count """
        counts = {}
        for sent in msg:
            f = sent.split(' ')
            for w in f:
                if (not counts.has_key(w)):
                    counts[w] = 0.0
                counts[w] += 1
        return counts


    def remove_repeats (self,msg):
        """ Remove Repeats """
        # twitter specific repeats
        msg = re.sub(r"(.)\1{2,}", r"\1\1\1", msg)  # characters repeated 3 or more times

        # laughs
        msg = re.sub(r"(ja|Ja)(ja|Ja)+(j)?", r"jaja", msg) # spanish
        msg = re.sub(r"(rs|Rs)(Rs|rs)+(r)?", r"rsrs", msg) # portugese
        msg = re.sub(r"(ha|Ha)(Ha|ha)+(h)?", r"haha", msg) # english

        return msg


    def splitter(self,ln):
        """ Line Splitter """
        # horridly simple splitter
        ln = ln.replace(". ", ".\n\n").replace("? ","?\n\n").replace("! ","!\n\n")
        ln = ln.replace('."', '."\n\n')
        f = ln.split("\n")
        fout = []
        for s in f:
            s = s.rstrip()
            s = re.sub(r'^\s+', '', s)
            if (s!=""):
                fout.append(s)
        return fout


    def convertUTF8_to_ascii(self,ln):
        """ UTF8 to ASCII Converter """
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
        
        out = out.encode('ascii','ignore')

        return out


    def update_utf8_rewrite_hash (self):
        """ Rewrite Hash """
        # Strictly speaking (and in python) any ascii character >= 128 is not valid
        # This tries to rewrite utf-8 chars to ascii in a rational manner

        self.rewrite_hash[u'\xA1'] = " "                # INVERTED EXCLAMATION MARK
        self.rewrite_hash[u'\xA2'] = " cents "          # CENT SIGNS
        self.rewrite_hash[u'\xA3'] = " pounds "         # POUND SIGN
        self.rewrite_hash[u'\xA4'] = " "                # CURRENCY SIGN
        self.rewrite_hash[u'\xA5'] = " yen "            # YEN SIGN

        self.rewrite_hash[u'\xA8'] = " "                # DIAERESIS
        self.rewrite_hash[u'\xA9'] = " "                # COPYRIGHT SIGN
        self.rewrite_hash[u'\xAA'] = " "                # FEMININE ORDINAL INDICATOR
        self.rewrite_hash[u'\xAB'] = " "                # LEFT-POINTING DOUBLE ANGLE QUOTATION MARK
        self.rewrite_hash[u'\xAE'] = " "                # REGISTERED SIGN
        self.rewrite_hash[u'\xAF'] = " "                # MACRON

        self.rewrite_hash[u'\xB0'] = " degrees "        # DEGREE SIGN
        self.rewrite_hash[u'\xB1'] = " plus-or-minus "  # PLUS-MINUS SIGN
        self.rewrite_hash[u'\xB2'] = " "	            # SUPERSCRIPT TWO
        self.rewrite_hash[u'\xB3'] = " ";	            # SUPERSCRIPT THREE
        self.rewrite_hash[u'\xB4'] = "'"		        # ACUTE ACCENT
        self.rewrite_hash[u'\xB5'] = " micro "          # MICRO SIGN
        self.rewrite_hash[u'\xB7'] = " "                # MIDDLE DOT
        self.rewrite_hash[u'\xB8'] = " "                # CEDILLA
        self.rewrite_hash[u'\xB9'] = " "                # SUPERSCRIPT ONE
        self.rewrite_hash[u'\xBA'] = " "                # MASCULINE ORDINAL INDICATOR
        self.rewrite_hash[u'\xBF'] = " "                # INVERTED QUESTION MARK

        self.rewrite_hash[u'\xC0'] = "A"                # LATIN CAPITAL LETTER A WITH GRAVE
        self.rewrite_hash[u'\xC1'] = "A"                # LATIN CAPITAL LETTER A WITH ACUTE
        self.rewrite_hash[u'\xC2'] = "A"                # LATIN CAPITAL LETTER A WITH CIRCUMFLEX
        self.rewrite_hash[u'\xC3'] = "A"                # LATIN CAPITAL LETTER A WITH TILDE
        self.rewrite_hash[u'\xC4'] = "A"                # LATIN CAPITAL LETTER A WITH DIAERESIS
        self.rewrite_hash[u'\xC5'] = "A"                # LATIN CAPITAL LETTER A WITH RING ABOVE
        self.rewrite_hash[u'\xC6'] = "AE"               # LATIN CAPITAL LETTER AE
        self.rewrite_hash[u'\xC7'] = "C"                # LATIN CAPITAL LETTER C WITH CEDILLA
        self.rewrite_hash[u'\xC8'] = "E"                # LATIN CAPITAL LETTER E WITH GRAVE
        self.rewrite_hash[u'\xC9'] = "E"                # LATIN CAPITAL LETTER E WITH ACUTE
        self.rewrite_hash[u'\xCA'] = "E"                # LATIN CAPITAL LETTER E WITH CIRCUMFLEX
        self.rewrite_hash[u'\xCB'] = "E"                # LATIN CAPITAL LETTER E WITH DIAERESIS
        self.rewrite_hash[u'\xCC'] = "I"                # LATIN CAPITAL LETTER I WITH GRAVE
        self.rewrite_hash[u'\xCD'] = "I"                # LATIN CAPITAL LETTER I WITH ACUTE
        self.rewrite_hash[u'\xCE'] = "I"                # LATIN CAPITAL LETTER I WITH CIRCUMFLEX
        self.rewrite_hash[u'\xCF'] = "I"                # LATIN CAPITAL LETTER I WITH DIAERESIS

        self.rewrite_hash[u'\xD0'] = "Th"               # LATIN CAPITAL LETTER ETH
        self.rewrite_hash[u'\xD1'] = "N"                # LATIN CAPITAL LETTER N WITH TILDE
        self.rewrite_hash[u'\xD2'] = "O"                # LATIN CAPITAL LETTER O WITH GRAVE
        self.rewrite_hash[u'\xD3'] = "O"                # LATIN CAPITAL LETTER O WITH ACUTE
        self.rewrite_hash[u'\xD4'] = "O"                # LATIN CAPITAL LETTER O WITH CIRCUMFLEX
        self.rewrite_hash[u'\xD5'] = "O"                # LATIN CAPITAL LETTER O WITH TILDE
        self.rewrite_hash[u'\xD6'] = "O"                # LATIN CAPITAL LETTER O WITH DIAERESIS
        self.rewrite_hash[u'\xD7'] = "x"                # MULTIPLICATION SIGN
        self.rewrite_hash[u'\xD8'] = "O"                # LATIN CAPITAL LETTER O WITH STROKE
        self.rewrite_hash[u'\xD9'] = "U"                # LATIN CAPITAL LETTER U WITH GRAVE
        self.rewrite_hash[u'\xDA'] = "U"                # LATIN CAPITAL LETTER U WITH ACUTE
        self.rewrite_hash[u'\xDB'] = "U"                # LATIN CAPITAL LETTER U WITH CIRCUMFLEX
        self.rewrite_hash[u'\xDC'] = "U"                # LATIN CAPITAL LETTER U WITH DIAERESIS    
        self.rewrite_hash[u'\xDD'] = "Y"                # LATIN CAPITAL LETTER Y WITH ACUTE
        self.rewrite_hash[u'\xDE'] = "Th"               # LATIN CAPITAL LETTER THORN
        self.rewrite_hash[u'\xDF'] = "ss"               # LATIN SMALL LETTER SHARP S

        self.rewrite_hash[u'\xE0'] = "a"                # LATIN SMALL LETTER A WITH GRAVE
        self.rewrite_hash[u'\xE1'] = "a"                # LATIN SMALL LETTER A WITH ACUTE
        self.rewrite_hash[u'\xE2'] = "a"                # LATIN SMALL LETTER A WITH CIRCUMFLEX
        self.rewrite_hash[u'\xE3'] = "a"                # LATIN SMALL LETTER A WITH TILDE
        self.rewrite_hash[u'\xE4'] = "a"                # LATIN SMALL LETTER A WITH DIAERESIS
        self.rewrite_hash[u'\xE5'] = "a"                # LATIN SMALL LETTER A WITH RING ABOVE
        self.rewrite_hash[u'\xE6'] = "ae"               # LATIN SMALL LETTER AE
        self.rewrite_hash[u'\xE7'] = "c"                # LATIN SMALL LETTER C WITH CEDILLA
        self.rewrite_hash[u'\xE8'] = "e"                # LATIN SMALL LETTER E WITH GRAVE
        self.rewrite_hash[u'\xE9'] = "e"                # LATIN SMALL LETTER E WITH ACUTE
        self.rewrite_hash[u'\xEA'] = "e"                # LATIN SMALL LETTER E WITH CIRCUMFLEX
        self.rewrite_hash[u'\xEB'] = "e"                # LATIN SMALL LETTER E WITH DIAERESIS
        self.rewrite_hash[u'\xEC'] = "i"                # LATIN SMALL LETTER I WITH GRAVE
        self.rewrite_hash[u'\xED'] = "i"                # LATIN SMALL LETTER I WITH ACUTE
        self.rewrite_hash[u'\xEE'] = "i"                # LATIN SMALL LETTER I WITH CIRCUMFLEX
        self.rewrite_hash[u'\xEF'] = "i"                # LATIN SMALL LETTER I WITH DIAERESIS

        self.rewrite_hash[u'\xF0'] = "th"               # LATIN SMALL LETTER ETH
        self.rewrite_hash[u'\xF1'] = "n"                # LATIN SMALL LETTER N WITH TILDE
        self.rewrite_hash[u'\xF2'] = "o"                # LATIN SMALL LETTER O WITH GRAVE
        self.rewrite_hash[u'\xF3'] = "o"                # LATIN SMALL LETTER O WITH ACUTE
        self.rewrite_hash[u'\xF4'] = "o"                # LATIN SMALL LETTER O WITH CIRCUMFLEX
        self.rewrite_hash[u'\xF5'] = "o"                # LATIN SMALL LETTER O WITH TILDE
        self.rewrite_hash[u'\xF6'] = "o"                # LATIN SMALL LETTER O WITH DIAERESIS
        self.rewrite_hash[u'\xF7'] = " divided by "     # DIVISION SIGN
        self.rewrite_hash[u'\xF8'] = "o"                # LATIN SMALL LETTER O WITH STROKE
        self.rewrite_hash[u'\xF9'] = "u"                # LATIN SMALL LETTER U WITH GRAVE
        self.rewrite_hash[u'\xFA'] = "u"                # LATIN SMALL LETTER U WITH ACUTE
        self.rewrite_hash[u'\xFB'] = "u"                # LATIN SMALL LETTER U WITH CIRCUMFLEX
        self.rewrite_hash[u'\xFC'] = "u"                # LATIN SMALL LETTER U WITH DIAERESIS
        self.rewrite_hash[u'\xFD'] = "y"                # LATIN SMALL LETTER Y WITH ACUTE
        self.rewrite_hash[u'\xFE'] = "th"               # LATIN SMALL LETTER THORN
        self.rewrite_hash[u'\xFF'] = "y"                # LATIN SMALL LETTER Y WITH DIAERESIS

        self.rewrite_hash[u'\u0100'] = "A"              # LATIN CAPTIAL LETTER A WITH MACRON
        self.rewrite_hash[u'\u0101'] = "a"              # LATIN SMALL LETTER A WITH MACRON
        self.rewrite_hash[u'\u0102'] = "A"              # LATIN CAPITAL LETTER A WITH BREVE
        self.rewrite_hash[u'\u0103'] = "a"              # LATIN SMALL LETTER A WITH BREVE
        self.rewrite_hash[u'\u0104'] = "A"              # LATIN CAPITAL LETTER A WITH OGONEK
        self.rewrite_hash[u'\u0105'] = "a"              # LATIN SMALL LETTER A WITH OGONEK
        self.rewrite_hash[u'\u0106'] = "C"              # LATIN CAPITAL LETTER C WITH ACUTE
        self.rewrite_hash[u'\u0107'] = "c"              # LATIN SMALL LETTER C WITH ACUTE
        self.rewrite_hash[u'\u0108'] = "C"              # LATIN CAPITAL LETTER C WITH CIRCUMFLEX
        self.rewrite_hash[u'\u0109'] = "c"              # LATIN SMALL LETTER C WITH CIRCUMFLEX 
        self.rewrite_hash[u'\u010A'] = "C"              # LATIN CAPITAL LETTER C WITH DOT ABOVE
        self.rewrite_hash[u'\u010B'] = "c"              # LATIN SMALL LETTER C WITH DOT ABOVE
        self.rewrite_hash[u'\u010C'] = "C"              # LATIN CAPITAL LETTER C WITH CARON
        self.rewrite_hash[u'\u010D'] = "c"              # LATIN SMALL LETTER C WITH CARON
        self.rewrite_hash[u'\u010E'] = "D"              # LATIN CAPITAL LETTER D WITH CARON
        self.rewrite_hash[u'\u010F'] = "d"              # LATIN SMALL LETTER D WITH CARON

        self.rewrite_hash[u'\u0110'] = "D"              # LATIN CAPITAL LETTER D WITH STROKE
        self.rewrite_hash[u'\u0111'] = "d"              # LATIN SMALL LETTER D WITH STROKE
        self.rewrite_hash[u'\u0112'] = "E"              # LATIN CAPITAL LETTER E WITH MACRON
        self.rewrite_hash[u'\u0113'] = "e"              # LATIN SMALL LETTER E WITH MACRON
        self.rewrite_hash[u'\u0114'] = "E"              # LATIN CAPITAL LETTER E WITH BREVE
        self.rewrite_hash[u'\u0115'] = "e"              # LATIN SMALL LETTER E WITH BREVE
        self.rewrite_hash[u'\u0116'] = "E"              # LATIN CAPITAL LETTER E WITH DOT ABOVE
        self.rewrite_hash[u'\u0117'] = "e"              # LATIN SMALL LETTER E WITH DOT ABOVE
        self.rewrite_hash[u'\u0118'] = "E"              # LATIN CAPITAL LETTER E WITH OGONEK
        self.rewrite_hash[u'\u0119'] = "e"              # LATIN SMALL LETTER E WITH OGONEK
        self.rewrite_hash[u'\u011A'] = "E"              # LATIN CAPITAL LETTER E WITH CARON
        self.rewrite_hash[u'\u011B'] = "e"              # LATIN SMALL LETTER E WITH CARON
        self.rewrite_hash[u'\u011C'] = "G"              # LATIN CAPITAL LETTER G WITH CIRCUMFLEX
        self.rewrite_hash[u'\u011D'] = "g"              # LATIN SMALL LETTER G WITH CIRCUMFLEX
        self.rewrite_hash[u'\u011E'] = "G"              # LATIN CAPITAL LETTER G WITH BREVE 
        self.rewrite_hash[u'\u011F'] = "g"              # LATIN SMALL LETTER G WITH BREVE

        self.rewrite_hash[u'\u0120'] = "G"              # LATIN CAPITAL LETTER G WITH DOT ABOVE
        self.rewrite_hash[u'\u0121'] = "g"              # LATIN SMALL LETTER G WITH DOT ABOVE
        self.rewrite_hash[u'\u0122'] = "G"              # LATIN CAPITAL LETTER G WITH CEDILLA
        self.rewrite_hash[u'\u0123'] = "g"              # LATIN SMALL LETTER G WITH CEDILLA
        self.rewrite_hash[u'\u0124'] = "H"              # LATIN CAPITAL LETTER H WITH CIRCUMFLEX
        self.rewrite_hash[u'\u0125'] = "h"              # LATIN SMALL LETTER H WITH CIRCUMFLEX
        self.rewrite_hash[u'\u0126'] = "H"              # LATIN CAPITAL LETTER H WITH STROKE
        self.rewrite_hash[u'\u0127'] = "h"              # LATIN SMALL LETTER H WITH STROKE
        self.rewrite_hash[u'\u0128'] = "I"              # LATIN CAPITAL LETTER I WITH TILDE
        self.rewrite_hash[u'\u0129'] = "i"              # LATIN SMALL LETTER I WITH TILDE
        self.rewrite_hash[u'\u012A'] = "I"              # LATIN CAPITAL LETTER I WITH MACRON
        self.rewrite_hash[u'\u012B'] = "i"              # LATIN SMALL LETTER I WITH MACRON
        self.rewrite_hash[u'\u012C'] = "I"              # LATIN CAPITAL LETTER I WITH BREVE
        self.rewrite_hash[u'\u012D'] = "i"              # LATIN SMALL LETTER I WITH BREVE
        self.rewrite_hash[u'\u012E'] = "I"              # LATIN CAPITAL LETTER I WITH OGONEK
        self.rewrite_hash[u'\u012F'] = "i"              # LATIN SMALL LETTER I WITH OGONEK

        self.rewrite_hash[u'\u0130'] = "I"              # LATIN CAPITAL LETTER I WITH DOT ABOVE
        self.rewrite_hash[u'\u0131'] = "i"              # LATIN SMALL LETTER DOTLESS I
        self.rewrite_hash[u'\u0132'] = "IJ"             # LATIN CAPITAL LIGATURE IJ
        self.rewrite_hash[u'\u0133'] = "ij"             # LATIN SMALL LIGATURE IJ
        self.rewrite_hash[u'\u0134'] = "J"              # LATIN CAPITAL LETTER J WITH CIRCUMFLEX
        self.rewrite_hash[u'\u0135'] = "j"              # LATIN SMALL LETTER J WITH CIRCUMFLEX
        self.rewrite_hash[u'\u0136'] = "K"              # LATIN CAPITAL LETTER K WITH CEDILLA
        self.rewrite_hash[u'\u0137'] = "k"              # LATIN SMALL LETTER K WITH CEDILLA
        self.rewrite_hash[u'\u0138'] = "k"              # LATIN SMALL LETTER KRA
        self.rewrite_hash[u'\u0139'] = "L"              # LATIN CAPITAL LETTER L WITH ACUTE
        self.rewrite_hash[u'\u013A'] = "l"              # LATIN SMALL LETTER L WITH ACUTE
        self.rewrite_hash[u'\u013B'] = "L"              # LATIN CAPITAL LETTER L WITH CEDILLA
        self.rewrite_hash[u'\u013C'] = "l"              # LATIN SMALL LETTER L WITH CEDILLA
        self.rewrite_hash[u'\u013D'] = "L"              # LATIN CAPITAL LETTER L WITH CARON
        self.rewrite_hash[u'\u013E'] = "l"              # LATIN SMALL LETTER L WITH CARON
        self.rewrite_hash[u'\u013F'] = "L"              # LATIN CAPITAL LETTER L WITH MIDDLE DOT

        self.rewrite_hash[u'\u0140'] = "l"              # LATIN SMALL LETTER L WITH MIDDLE DOT
        self.rewrite_hash[u'\u0141'] = "L"              # LATIN CAPITAL LETTER L WITH STROKE
        self.rewrite_hash[u'\u0142'] = "l"              # LATIN SMALL LETTER L WITH STROKE
        self.rewrite_hash[u'\u0143'] = "N"              # LATIN CAPITAL LETTER N WITH ACUTE
        self.rewrite_hash[u'\u0144'] = "n"              # LATIN SMALL LETTER N WITH ACUTE
        self.rewrite_hash[u'\u0145'] = "N"              # LATIN CAPITAL LETTER N WITH CEDILLA
        self.rewrite_hash[u'\u0146'] = "n"              # LATIN SMALL LETTER N WITH CEDILLA
        self.rewrite_hash[u'\u0147'] = "N"              # LATIN CAPITAL LETTER N WITH CARON
        self.rewrite_hash[u'\u0148'] = "n"              # LATIN SMALL LETTER N WITH CARON
        self.rewrite_hash[u'\u0149'] = "n"              # LATIN SMALL LETTER N PRECEDED BY APOSTROPHE
        self.rewrite_hash[u'\u014A'] = "N"              # LATIN CAPITAL LETTER ENG
        self.rewrite_hash[u'\u014B'] = "n"              # LATIN SMALL LETTER ENG
        self.rewrite_hash[u'\u014C'] = "O"              # LATIN CAPITAL LETTER O WITH MACRON
        self.rewrite_hash[u'\u014D'] = "o"              # LATIN SMALL LETTER O WITH MACRON
        self.rewrite_hash[u'\u014E'] = "O"              # LATIN CAPITAL LETTER O WITH BREVE
        self.rewrite_hash[u'\u014F'] = "o"              # LATIN SMALL LETTER O WITH BREVE

        self.rewrite_hash[u'\u0150'] = "O"              # LATIN CAPITAL LETTER O WITH DOUBLE ACUTE
        self.rewrite_hash[u'\u0151'] = "o"              # LATIN SMALL LETTER O WITH DOUBLE ACUTE
        self.rewrite_hash[u'\u0152'] = "oe"             # LATIN CAPITAL LIGATURE OE
        self.rewrite_hash[u'\u0153'] = "oe"             # LATIN SMALL LIGATURE OE
        self.rewrite_hash[u'\u0153'] = "R"              # LATIN CAPITAL LETTER R WITH ACUTE
        self.rewrite_hash[u'\u0154'] = "R"              # LATIN CAPITAL LETTER R WITH ACUTE
        self.rewrite_hash[u'\u0155'] = "r"              # LATIN SMALL LETTER R WITH ACUTE
        self.rewrite_hash[u'\u0156'] = "R"              # LATIN CAPITAL LETTER R WITH CEDILLA
        self.rewrite_hash[u'\u0157'] = "r"              # LATIN SMALL LETTER R WITH CEDILLA
        self.rewrite_hash[u'\u0158'] = "R"              # LATIN CAPITAL LETTER R WITH CARON
        self.rewrite_hash[u'\u0159'] = "r"              # LATIN SMALL LETTER R WITH CARON
        self.rewrite_hash[u'\u015A'] = "S"              # LATIN CAPITAL LETTER S WITH ACUTE
        self.rewrite_hash[u'\u015B'] = "s"              # LATIN SMALL LETTER S WITH ACUTE
        self.rewrite_hash[u'\u015C'] = "S"              # LATIN CAPITAL LETTER S WITH CIRCUMFLEX
        self.rewrite_hash[u'\u015D'] = "s"              # LATIN SMALL LETTER S WITH CIRCUMFLEX
        self.rewrite_hash[u'\u015E'] = "S"              # LATIN CAPITAL LETTER S WITH CEDILLA
        self.rewrite_hash[u'\u015F'] = "s"              # LATIN SMALL LETTER S WITH CEDILLA

        self.rewrite_hash[u'\u0160'] = "S"              # LATIN CAPITAL LETTER S WITH CARON
        self.rewrite_hash[u'\u0161'] = "s"              # LATIN SMALL LETTER S WITH CARON
        self.rewrite_hash[u'\u0162'] = "T"              # LATIN CAPITAL LETTER T WITH CEDILLA 
        self.rewrite_hash[u'\u0163'] = "t"              # LATIN SMALL LETTER T WITH CEDILLA
        self.rewrite_hash[u'\u0164'] = "T"              # LATIN CAPITAL LETTER T WITH CARON
        self.rewrite_hash[u'\u0165'] = "t"              # LATIN SMALL LETTER T WITH CARON
        self.rewrite_hash[u'\u0166'] = "T"              # LATIN CAPITAL LETTER T WITH STROKE
        self.rewrite_hash[u'\u0167'] = "t"              # LATIN SMALL LETTER T WITH STROKE
        self.rewrite_hash[u'\u0168'] = "U"              # LATIN CAPITAL LETTER U WITH TILDE
        self.rewrite_hash[u'\u0169'] = "u"              # LATIN SMALL LETTER U WITH TILDE
        self.rewrite_hash[u'\u016A'] = "U"              # LATIN CAPITAL LETTER U WITH MACRON
        self.rewrite_hash[u'\u016B'] = "u"              # LATIN SMALL LETTER U WITH MACRON
        self.rewrite_hash[u'\u016C'] = "U"              # LATIN CAPITAL LETTER U WITH BREVE
        self.rewrite_hash[u'\u016D'] = "u"              # LATIN SMALL LETTER U WITH BREVE
        self.rewrite_hash[u'\u016E'] = "U"              # LATIN CAPITAL LETTER U WITH RING ABOVE
        self.rewrite_hash[u'\u016F'] = "u"              # LATIN SMALL LETTER U WITH RING ABOVE

        self.rewrite_hash[u'\u0170'] = "U"              # LATIN CAPITAL LETTER U WITH DOUBLE ACUTE
        self.rewrite_hash[u'\u0171'] = "u"              # LATIN SMALL LETTER U WITH DOUBLE ACUTE
        self.rewrite_hash[u'\u0172'] = "U"              # LATIN CAPITAL LETTER U WITH OGONEK
        self.rewrite_hash[u'\u0173'] = "u"              # LATIN SMALL LETTER U WITH OGONEK
        self.rewrite_hash[u'\u0174'] = "W"              # LATIN CAPITAL LETTER W WITH CIRCUMFLEX
        self.rewrite_hash[u'\u0175'] = "w"              # LATIN SMALL LETTER W WITH CIRCUMFLEX
        self.rewrite_hash[u'\u0176'] = "Y"              # LATIN CAPITAL LETTER Y WITH CIRCUMFLEX
        self.rewrite_hash[u'\u0177'] = "y"              # LATIN SMALL LETTER Y WITH CIRCUMFLEX
        self.rewrite_hash[u'\u0178'] = "Y"              # LATIN CAPITAL LETTER Y WITH DIAERESIS
        self.rewrite_hash[u'\u0179'] = "Z"              # LATIN CAPITAL LETTER Z WITH ACUTE
        self.rewrite_hash[u'\u017A'] = "z"              # LATIN SMALL LETTER Z WITH ACUTE
        self.rewrite_hash[u'\u017B'] = "Z"              # LATIN CAPITAL LETTER Z WITH DOT ABOVE
        self.rewrite_hash[u'\u017C'] = "z"              # LATIN SMALL LETTER Z WITH DOT ABOVE
        self.rewrite_hash[u'\u017D'] = "Z"              # LATIN CAPITAL LETTER Z WITH CARON
        self.rewrite_hash[u'\u017E'] = "z"              # LATIN SMALL LETTER Z WITH CARON
        self.rewrite_hash[u'\u017F'] = "s"              # LATIN SMALL LETTER LONG S

        self.rewrite_hash[u'\u0180'] = "b"              # LATIN SMALL LETTER B WITH STROKE
        self.rewrite_hash[u'\u0181'] = "B"              # LATIN CAPITAL LETTER B WITH HOOK
        self.rewrite_hash[u'\u0182'] = "B"              # LATIN CAPITAL LETTER B WITH TOPBAR
        self.rewrite_hash[u'\u0183'] = "b"              # LATIN SMALL LETTER B WITH TOPBAR
        self.rewrite_hash[u'\u0184'] = "b"              # LATIN CAPITAL LETTER TONE SIX
        self.rewrite_hash[u'\u0185'] = "b"              # LATIN SMALL LETTER TONE SIX  
        self.rewrite_hash[u'\u0186'] = "O"              # LATIN CAPITAL LETTER OPEN O
        self.rewrite_hash[u'\u0187'] = "C"              # LATIN CAPITAL LETTER C WITH HOOK
        self.rewrite_hash[u'\u0188'] = "c"              # LATIN SMALL LETTER C WITH HOOK
        self.rewrite_hash[u'\u0189'] = "D"              # LATIN CAPITAL LETTER AFRICAN D
        self.rewrite_hash[u'\u018A'] = "D"              # LATIN CAPITAL LETTER D WITH HOOK
        self.rewrite_hash[u'\u018B'] = "d"              # LATIN CAPITAL LETTER D WITH TOPBAR
        self.rewrite_hash[u'\u018C'] = "d"              # LATIN SMALL LETTER D WITH TOPBAR
        self.rewrite_hash[u'\u018D'] = " "              # LATIN SMALL LETTER TURNED DELTA
        self.rewrite_hash[u'\u018E'] = " "              # LATIN CAPITAL LETTER REVERSED E
        self.rewrite_hash[u'\u018F'] = " "              # LATIN CAPITAL LETTER SCHWA

        self.rewrite_hash[u'\u0190'] = "E"              # LATIN CAPITAL LETTER OPEN E
        self.rewrite_hash[u'\u0191'] = "F"              # LATIN CAPITAL LETTER F WITH HOOK
        self.rewrite_hash[u'\u0192'] = "f"              # LATIN SMALL LETTER F WITH HOOK
        self.rewrite_hash[u'\u0193'] = "G"              # LATIN CAPITAL LETTER G WITH HOOK
        self.rewrite_hash[u'\u0194'] = " "              # LATIN CAPITAL LETTER GAMMA
        self.rewrite_hash[u'\u0195'] = "hv"             # LATIN SMALL LETTER HV
        self.rewrite_hash[u'\u0196'] = "I"              # LATIN CAPITAL LETTER IOTA
        self.rewrite_hash[u'\u0197'] = "I"              # LATIN CAPITAL LETTER I WITH STROKE
        self.rewrite_hash[u'\u0198'] = "K"              # LATIN CAPITAL LETTER K WITH HOOK
        self.rewrite_hash[u'\u0199'] = "k"              # LATIN SMALL LETTER K WITH HOOK
        self.rewrite_hash[u'\u019A'] = "l"              # LATIN SMALL LETTER L WITH BAR
        self.rewrite_hash[u'\u019B'] = " "              # LATIN SMALL LETTER LAMBDA WITH STROKE
        self.rewrite_hash[u'\u019C'] = " "              # LATIN CAPITAL LETTER TURNED M
        self.rewrite_hash[u'\u019D'] = "N"              # LATIN CAPITAL LETTER N WITH LEFT HOOK
        self.rewrite_hash[u'\u019E'] = "n"              # LATIN SMALL LETTER N WITH LONG RIGHT LEG
        self.rewrite_hash[u'\u019F'] = "O"              # LATIN CAPITAL LETTER O WITH MIDDLE TILDE

        self.rewrite_hash[u'\u0226'] = "a"              # LATIN CAPITAL LETTER A WITH DOT ABOVE
        self.rewrite_hash[u'\u0227'] = "a"              # LATIN SMALL LETTER A WITH DOT ABOVE
        self.rewrite_hash[u'\u02DC'] = " "              # SMALL TILDE 

        self.rewrite_hash[u'\u0391'] = "A"              # GREEK CAPITAL LETTER ALPHA
        self.rewrite_hash[u'\u03A4'] = "T"              # GREEK CAPITAL LETTER TAU
        self.rewrite_hash[u'\u03A9'] = " omega "        # GREEK CAPITAL LETTER OMEGA
        self.rewrite_hash[u'\u03B2'] = " beta "         # GREEK SMALL LETTER BETA
        self.rewrite_hash[u'\u03BC'] = " mu "           # GREEK SMALL LETTER MU
        self.rewrite_hash[u'\u03C0'] = " pi "           # GREEK SMALL LETTER PI

        self.rewrite_hash[u'\u0441'] = "c"              # CYRILLIC SMALL LETTER ES

        self.rewrite_hash[u'\u1F7B'] = "u"              # GREEK SMALL LETTER UPSILON WITH OXIA    
        self.rewrite_hash[u'\u1E25'] = "h"              # LATIN SMALL LETTER H WITH DOT BELOW
        self.rewrite_hash[u'\u1ECB'] = "i"              # LATIN SMALL LETTER I WITH DOT BELOW

        self.rewrite_hash[u'\u2018'] = "'"              # LEFT SINGLE QUOTATION MARK
        self.rewrite_hash[u'\u2019'] = "'"              # RIGHT SINGLE QUOTATION MARK
        self.rewrite_hash[u'\u201A'] = " "              # SINGLE LOW-9 QUOTATION MARK
        self.rewrite_hash[u'\u201C'] = " "              # LEFT DOUBLE QUOTATION MARK
        self.rewrite_hash[u'\u201D'] = " "              # RIGHT DOUBLE QUOTATION MARK
        self.rewrite_hash[u'\u201E'] = " "              # DOUBLE LOW-9 QUOTATION MARK
        self.rewrite_hash[u'\u201F'] = " "              # OUBLE HIGH-REVERSED-9 QUOTATION MARK

        self.rewrite_hash[u'\u2032'] = "\'"             # PRIME
        self.rewrite_hash[u'\u2033'] = " "              # DOUBLE PRIME

        self.rewrite_hash[u'\u20AC'] = " euros "        # EURO SIGN

        self.rewrite_hash[u'\u2122'] = " "              # TRADE MARK SIGN

        self.rewrite_hash[u'\uFB01'] = "fi"             # LATIN SMALL LIGATURE FI
        self.rewrite_hash[u'\uFF00'] = " "              # 

        return 


    def remove_word_punctuation (self,ln):
        """ Punctuation Remover """
        ln = re.sub("^(\S+)[\.\!\?]", "\g<1>", ln)
        ln = re.sub("\s(\S+)[\.\!\?]", " \g<1>", ln)
        ln = re.sub("(\S+)[\.\!\?]$", "\g<1>", ln)
        ln = re.sub("\s[\.\!\?]\s", " ", ln)
        ln = re.sub("^[\.\!\?]$", "", ln)

        # Clean up extra spaces
        ln = re.sub('^\s+', '', ln)
        ln = re.sub('\s+$', '', ln)
        ln = re.sub('\s+', ' ', ln)

        return ln


    def remove_twitter_meta (self,ln):
        """ Twitter Metadata Remover """
        # ln = re.sub(r'\#\S+', ' ', ln) # remove hashtags --old version
        # ln = re.sub(r'\@\S+', ' ', ln) # remove @tags -- old version

        # ln = re.sub(r'\#[a-zA-Z0-9_]+', ' ', ln) # remove hashtags
        ln = re.sub(r'\@[a-zA-Z0-9_]+', ' ', ln) # remove @tags
        ln = re.sub('\sRT\s', ' ', ln) # remove retweet marker
        ln = re.sub('^RT\s', ' ', ln)

        # Clean up extra spaces
        ln = re.sub('^\s+', '', ln)
        ln = re.sub('\s+$', '', ln)
        ln = re.sub('\s+', ' ', ln)
        return ln


    def remove_nonsentential_punctuation (self,ln):
        """ Remove non-sentential punctuation """

        # remove '-'
        ln = re.sub('^\-+', '', ln)
        ln = re.sub('\-\-+', '', ln)
        ln = re.sub('\s\-+', '', ln)

        # remove '~'
        ln = re.sub('\~', ' ', ln)

        # remove standard double quotes
        ln = re.sub('\"', '', ln)

        # remove single quotes
        ln = re.sub("^\'+", '', ln)
        ln = re.sub("\'+$", '', ln)
        ln = re.sub("\'+\s+", ' ', ln)
        ln = re.sub("\s+\'+", ' ', ln)
        ln = re.sub("\s+\`+", ' ', ln)
        ln = re.sub("^\`+", ' ', ln)

        # remove ':'
        ln = re.sub("\:\s", " ", ln)
        ln = re.sub("\:$", "", ln)

        # remove ';'
        ln = re.sub('\;\s', ' ', ln)
        ln = re.sub('\;$', '', ln)

        # remove '_'
        ln = re.sub('\_+\s', ' ', ln)
        ln = re.sub('^\_+', '', ln)
        ln = re.sub('_+$', '', ln)
        ln = re.sub('\_\_+', ' ', ln)

        # remove ','
        ln = re.sub('\,+([\#A-Za-z])', ' \g<1>', ln) 
        ln = re.sub('\,+$', ' ', ln)
        ln = re.sub('\,\.\s', ' ', ln)
        ln = re.sub('\,\s', ' ', ln)

        # remove '*'
        ln = re.sub('\s\*+', ' ', ln)
        ln = re.sub('\*+\s', ' ', ln)
        ln = re.sub('\*\.', ' ', ln)
        ln = re.sub('\s\*+\s', ' ', ln)
        ln = re.sub('^\*+', '', ln)
        ln = re.sub('\*+$', '', ln)

        # Keep only one '.', '?', or '!' 
        ln = re.sub('\?[\!\?]+', '?', ln)
        ln = re.sub('\![\?\!]+', '!', ln)
        ln = re.sub('\.\.+', '.', ln)

        # # remove '/'
        ln = re.sub('\s\/', ' ', ln)
        ln = re.sub('\/\s', ' ', ln)

        # remove sentence final '!' and '?' 
        # ln = re.sub('[\!\?]+\s*$', '', ln)
        
        # remove other special characters
        ln = re.sub('\|', ' ', ln)
        ln = re.sub(r'\\', ' ', ln)

        # Remove parentheses that are not part of emoticons.
        # Note sure of the best way to do this, but here's a conservative 
        # approach.
        ln = re.sub('\(([@\#A-Za-z0-9])', '\g<1>', ln)
        ln = re.sub('([@\#A-Za-z0-9])\)', '\g<1> ', ln)

        # Clean up extra spaces
        ln = re.sub('^\s+', '', ln)
        ln = re.sub('\s+$', '', ln)
        ln = re.sub('\s+', ' ', ln)

        return ln

