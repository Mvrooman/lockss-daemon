#!/usr/bin/awk -f
# cat *.tdb | tdbout -t publisher,plugin,publisher:info[contract],year,publisher:info[tester],status,publisher:info[back]

BEGIN {
  FS="\t"
  pn = 0
}

{
  # add a loop to add line only if ending year is gt or eq to contract year
  current_year = 2013
  end_year = 0
  incontract = 0
  test_year = ""
  if ($7 == "") {
    test_year = $3
  } else {
    test_year = $7
  }
  if (length($4) > 3) {
    end_year = substr($4,length($4)-3,4)
  }
  #printf "%s\t%s\t%s\t%s\t%s\t%s\t%s\n", $1,$2,$3,$4,$7,end_year,test_year
  if (end_year >= test_year) {
    if ((end_year < current_year) || ((end_year >= current_year) && (incl_cur == 1))) {
      incontract = 1
    }
  } 

  if (incontract == 1) {
      nn = split($2,na,/\./)
      lp2 = na[nn]
    if (!(($1,lp2,$4) in b)) {
      p[pn] = $1
      n[pn] = lp2
  #    n[pn] = $2
      t[pn] = $3
      k[pn] = $7
      d[pn] = $4
      r[pn] = $5
      pn++
    }
    b[$1,lp2,$4]++
    c[$1,lp2,$4,$6]++
    x[$6]++
    tt++
  }
}

END {
  s[0] = "expected"
  s[1] = "exists"
  s[2] = "manifest"
  s[3] = "wanted"
  s[4] = "crawling"
  s[5] = "testing"
  s[6] = "notReady"
  s[7] = "released"
  s[8] = "down"
  s[9] = "superseded"
  s[10] = "zapped"
  sn = 11
  
  sc[0] = "expe"
  sc[1] = "exis"
  sc[2] = "mani"
  sc[3] = "want"
  sc[4] = "craw"
  sc[5] = "test"
  sc[6] = "notR"
  sc[7] = "rele"
  sc[8] = "down"
  sc[9] = "supe"
  sc[10] = "zapp"
  scn = 11

  #print out header
  printf "Publisher\tPlugin\tContr\tBack\tYear\tT\tTotal"
  for (j = 0 ; j < scn ; j++) {
    if (x[s[j]] > 0) {
    printf "\t%s", sc[j]
    }
  }
  printf "\n"

  #print out publisher, plugin, contract year, back, year, tester, total aus
  for (i = 0 ; i < pn ; i++) {
    printf "%s\t%s\t%s\t%s\t%s\t%s\t%d", p[i], n[i], t[i], k[i], d[i], r[i], b[p[i],n[i],d[i]]
    for (j = 0 ; j < sn ; j++) {
      if (x[s[j]] > 0){
      if (c[p[i],n[i],d[i],s[j]] == 0) {
      printf "\t.." 
    } else {
        printf "\t%d", c[p[i],n[i],d[i],s[j]]
      }
    }
    }
    printf "\n"
  }
    #print out bottom line sums
    printf "Publisher\tPlugin\tContr\tBack\tYear\tT\t%d", tt
    for (j = 0 ; j < sn ; j++) {
      if (x[s[j]] > 0) {
        printf "\t%d", x[s[j]]
      }
    }

  printf "\n"
}

