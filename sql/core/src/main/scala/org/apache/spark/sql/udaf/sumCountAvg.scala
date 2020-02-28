/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.spark.sql.udaf

import java.util

import org.apache.spark.sql.Row
import org.apache.spark.sql.expressions.{MutableAggregationBuffer, UserDefinedAggregateFunction}
import org.apache.spark.sql.types.{DataType, DataTypes, StructType}

/**
 *
 */
class sumCountAvg extends UserDefinedAggregateFunction with Serializable{

  override def inputSchema: StructType = {
    DataTypes.createStructType(util.Arrays.asList(
      DataTypes.createStructField("food_amount", DataTypes.DoubleType, true),
      DataTypes.createStructField("channel_name", DataTypes.StringType, true)
    ))
  }

  override def bufferSchema: StructType = {
    DataTypes.createStructType(util.Arrays.asList(
      DataTypes.createStructField("sum_food_amount", DataTypes.DoubleType, true),
      DataTypes.createStructField("count_food_amount", DataTypes.IntegerType, true),
      DataTypes.createStructField("count_channel_name", DataTypes.IntegerType, true)))
  }

  override def dataType: DataType =
    DataTypes.DoubleType

  override def deterministic: Boolean = true

  override def initialize(buffer: MutableAggregationBuffer): Unit = {
    buffer.update(0, 0.00)
    buffer.update(1, 0)
    buffer.update(2, 0)
  }


  override def update(buffer: MutableAggregationBuffer, input: Row): Unit = {
    buffer.update(0, buffer.getDouble(0) + input.getDouble(0))
    buffer.update(1, buffer.getInt(1) + 1)
    buffer.update(2, buffer.getInt(2) + 1)
  }


  override def merge(buffer1: MutableAggregationBuffer, buffer2: Row): Unit = {
    buffer1.update(0, buffer1.getDouble(0) + buffer2.getDouble(0))
    buffer1.update(1, buffer1.getInt(1) + buffer2.getInt(1))
    buffer1.update(2, buffer1.getInt(2) + buffer2.getInt(2))
  }

  override def evaluate(row: Row): Any = {
    row.getDouble(0) + row.getInt(2) +
      row.getDouble(0) / row.getInt(1)
  }
}
