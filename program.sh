#!/bin/bash

repl(){
  clj \
    -J-Dclojure.core.async.pool-size=8 \
    -X:Ripley Ripley.core/process \
    :main-ns Philoctetes.main
}


main(){
  clojure \
    -J-Dclojure.core.async.pool-size=8 \
    -M -m Philoctetes.main
}

jar(){

  rm -rf out/*.jar out/classes
  clojure \
    -X:Genie Genie.core/process \
    :main-ns Philoctetes.main \
    :filename "\"out/Philoctetes-$(git rev-parse --short HEAD).jar\"" \
    :paths '["src"]'
}

release(){
  jar
}

"$@"