#!/bin/sh

i=0
while [ $i -le 100 ] ;
do
   # run a few in background
   j=0
   while [ $j -le 10 ] ;
   do
     ./run-grpcurl.sh&
      j=$((j+1));
   done
   # wait until completed
   ./run-grpcurl.sh
   i=$((i+1));
   sleep 0.02
done
