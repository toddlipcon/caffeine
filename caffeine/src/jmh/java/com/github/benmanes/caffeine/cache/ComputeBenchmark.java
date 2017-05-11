/*
 * Copyright 2014 Ben Manes. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.benmanes.caffeine.cache;

import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.rapidoid.cache.Caching;
import org.rapidoid.lambda.Mapper;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.yahoo.ycsb.generator.NumberGenerator;
import com.yahoo.ycsb.generator.ScrambledZipfianGenerator;

/**
 * @author ben.manes@gmail.com (Ben Manes)
 */
@State(Scope.Benchmark)
public class ComputeBenchmark {
  static final int CAPACITY = 1024 * 1024;
  static final Mapper<Integer, Boolean> mapper = any -> Boolean.TRUE;
  static final Function<Integer, Boolean> mappingFunction = any -> Boolean.TRUE;
  static final CacheLoader<Integer, Boolean> cacheLoader = CacheLoader.from(key -> Boolean.TRUE);

  @Param({"Caffeine", "Guava"})
  String computeType;

  Function<Integer, Boolean> benchmarkFunction;

  Cache<Integer, Boolean> caffeineCache;

  @State(Scope.Thread)
  public static class ThreadState {
    final Random random = new Random();
  }


  @Setup
  public void setup() {
    if (computeType.equals("ConcurrentHashMap")) {
      setupConcurrentHashMap();
    } else if (computeType.equals("Caffeine")) {
      setupCaffeine();
    } else if (computeType.equals("Guava")) {
      setupGuava();
    } else if (computeType.equals("Rapidoid")) {
      setupRapidoid();
    } else {
      throw new AssertionError("Unknown computingType: " + computeType);
    }
  }

  @Benchmark @Threads(80)
  public Boolean compute_spread(ThreadState threadState) {
    // Generate a key between 0 and 1<22, but heavily skewed towards lower keys.
    // With capacity = 1M, this yields a ~95% hit ratio.
    Random r = threadState.random;
    int log = r.nextInt(22);
    int key = r.nextInt(1 << log);
    return benchmarkFunction.apply(key);
  }

  private void setupConcurrentHashMap() {
    ConcurrentMap<Integer, Boolean> map = new ConcurrentHashMap<>();
    benchmarkFunction = key -> map.computeIfAbsent(key, mappingFunction);
  }

  private void setupCaffeine() {
    caffeineCache = Caffeine.newBuilder()
        .maximumSize(CAPACITY)
        .build();
    benchmarkFunction = key -> caffeineCache.get(key, mappingFunction);
  }

  private void setupGuava() {
    com.google.common.cache.LoadingCache<Integer, Boolean> cache =
        CacheBuilder.newBuilder().concurrencyLevel(64)
        .maximumSize(CAPACITY)
        .build(cacheLoader);
    benchmarkFunction = cache::getUnchecked;
  }

  private void setupRapidoid() {
    org.rapidoid.cache.Cache<Integer, Boolean> cache = Caching.of(mapper).build();
    benchmarkFunction = cache::get;
  }
}
