#!/usr/bin/env bash
function run {
  if ! $1; then
    # return a non-zero value to indicate error
    exit 1
  fi
}

run ". build/envsetup.sh"
run "lunch lineage_rk3328-userdebug"
run "make -j8"
