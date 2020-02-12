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
import java.util.Calendar

import org.apache.spark.sql.Row
import org.apache.spark.sql.expressions.{MutableAggregationBuffer, UserDefinedAggregateFunction}
import org.apache.spark.sql.types.{DataType, DataTypes, StructType}

class CompareValueSum extends UserDefinedAggregateFunction {

  def getTime(date: String, date_type: String, count: Int): String = {
    val ca = Calendar.getInstance()
    ca.set(date.substring(0, 4).toInt,
      date.substring(4, 6).toInt,
      date.substring(6, 8).toInt)
    date_type match {
      case "year" => ca.add(Calendar.YEAR, count)
      case "month" => ca.add(Calendar.MONTH, count)
      case "week" => ca.add(Calendar.WEEK_OF_YEAR, count)
      case "day" => ca.add(Calendar.DAY_OF_YEAR, count)
      case "hour" => ca.add(Calendar.HOUR_OF_DAY, count)
      case _ => throw new Exception
    }

    var m = ca.get(Calendar.MONTH).toString
    var d = ca.get(Calendar.DAY_OF_MONTH).toString
    if (m.length < 2) {
      m = "0" + m
    }
    if (d.length < 2) {
      d = "0" + d
    }

    ca.get(Calendar.YEAR) + "" + m + "" + d
  }

  override def inputSchema: StructType =
    DataTypes.createStructType(util.Arrays.asList(
      DataTypes.createStructField("datestr", DataTypes.StringType, true),
      DataTypes.createStructField("value", DataTypes.LongType, true),
      DataTypes.createStructField("time_window1", DataTypes.LongType, true),
      DataTypes.createStructField("time_window2", DataTypes.LongType, true),
      DataTypes.createStructField("time_diff_type", DataTypes.StringType, true),
      DataTypes.createStructField("time_diff", DataTypes.IntegerType, true)
    ))

  override def bufferSchema: StructType =
    DataTypes.createStructType(util.Arrays.asList(
      DataTypes.createStructField("mid_result",
        DataTypes.createMapType(
          DataTypes.StringType,
          DataTypes.LongType,
          true), true),
      DataTypes.createStructField("time_window1", DataTypes.LongType, true),
      DataTypes.createStructField("time_window2", DataTypes.LongType, true),
      DataTypes.createStructField("time_diff_type", DataTypes.StringType, true),
      DataTypes.createStructField("time_diff", DataTypes.IntegerType, true)
    )
    )

  override def dataType: DataType =
    DataTypes.createMapType(DataTypes.StringType, DataTypes.LongType)

  override def deterministic: Boolean = true

  override def initialize(buffer: MutableAggregationBuffer): Unit = {
    buffer.update(0, Map())
    buffer.update(1, 0L)
    buffer.update(2, 0L)
    buffer.update(3, "")
    buffer.update(4, 0)
  }

  override def update(buffer: MutableAggregationBuffer, input: Row): Unit = {

    var cat = buffer.getAs[Map[String, Long]](0)

    val datestr = input.getAs[String](0)
    var value = input.getAs[Long](1)

    val i = cat.getOrElse(datestr, 0L)
    value = i + value
    cat += (datestr -> value)
    buffer.update(0, cat)
    buffer.update(1, input.getAs[Long](2))
    buffer.update(2, input.getAs[Long](3))
    buffer.update(3, input.getAs[String](4))
    buffer.update(4, input.getAs[Int](5))

  }

  override def merge(buffer1: MutableAggregationBuffer, buffer2: Row): Unit = {

    val cat1 = buffer1.getAs[Map[String, Long]](0)
    val cat2 = buffer2.getAs[Map[String, Long]](0)

    val dog1 = cat1 ++ cat2.map(t => t._1 -> (t._2 + cat1.getOrElse(t._1, 0L)))

    buffer1.update(0, dog1)

    buffer1.update(1, buffer2.getAs[Long](1))
    buffer1.update(2, buffer2.getAs[Long](2))
    buffer1.update(3, buffer2.getAs[String](3))
    buffer1.update(4, buffer2.getAs[Long](4))
  }

  override def evaluate(row: Row): Any = {
    val dog = row.getAs[Map[String, Long]](0)
    val time_window1 = row.getAs[Long](1)
    val time_window2 = row.getAs[Long](2)
    val time_diff_type = row.getAs[String](3)

    val time_diff = row.getAs[Int](4)

    val dog2 = dog.filter(p => p._1.toInt >= time_window1.toInt && p._2.toInt <= time_window2.toInt)
    var result = Map[String, Long]()
    dog2.foreach(f => {
      val date = f._1
      val value = f._2

      val date2 = getTime(date, time_diff_type, time_diff)
      val value2 = dog.getOrElse(date2, 0L)
      result += (date -> (value - value2))
    }
    )
    result
  }
}
