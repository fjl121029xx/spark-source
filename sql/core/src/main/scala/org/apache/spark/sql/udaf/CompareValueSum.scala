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

import java.text.SimpleDateFormat
import java.util
import java.util.Calendar
import java.util.regex.Pattern

import org.apache.spark.sql.Row
import org.apache.spark.sql.expressions.{MutableAggregationBuffer, UserDefinedAggregateFunction}
import org.apache.spark.sql.types.{DataType, DataTypes, StructType}

// scalastyle:off println
class CompareValueSum extends UserDefinedAggregateFunction with Serializable{


  def getTime(date: String, date_type: String, count: Int): String = {
    val ca = Calendar.getInstance()
    ca.set(date.substring(0, 4).toInt,
      date.substring(4, 6).toInt - 1,
      date.substring(6, 8).toInt)
    date_type match {
      case "year" => ca.add(Calendar.YEAR, count)
      case "month" => ca.add(Calendar.MONTH, count)
      case "week" => ca.add(Calendar.WEEK_OF_YEAR, count)
      case "day" => ca.add(Calendar.DAY_OF_YEAR, count)
      case "hour" => ca.add(Calendar.HOUR_OF_DAY, count)
      case _ => throw new Exception
    }

    var m = (ca.get(Calendar.MONTH)).toString
    var d = ca.get(Calendar.DAY_OF_MONTH).toString
    if (m.length < 2) {
      m = "0" + m
    }
    if (d.length < 2) {
      d = "0" + d
    }

    ca.get(Calendar.YEAR) + "" + m + "" + d
  }

  def getTime(ca: Calendar): String = {
    var m = (ca.get(Calendar.MONTH) + 1).toString
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
      DataTypes.createStructField("time_day", DataTypes.StringType, true),
      DataTypes.createStructField("value", DataTypes.DoubleType, true),
      DataTypes.createStructField("dimen_mode", DataTypes.StringType, true),
      DataTypes.createStructField("time_diff_type", DataTypes.StringType, true)
    ))

  override def bufferSchema: StructType =
    DataTypes.createStructType(util.Arrays.asList(
      DataTypes.createStructField(
        "mid_result",
        DataTypes.createMapType(DataTypes.StringType, DataTypes.DoubleType, true),
        true),
      DataTypes.createStructField("dimen_mode", DataTypes.StringType, true),
      DataTypes.createStructField("time_diff_type", DataTypes.StringType, true),

      DataTypes.createStructField("min_reportdate", DataTypes.IntegerType, true),
      DataTypes.createStructField("max_reportdate", DataTypes.IntegerType, true)
    )
    )

  override def dataType: DataType =
    DataTypes.createMapType(DataTypes.StringType, DataTypes.DoubleType)

  override def deterministic: Boolean = true

  override def initialize(buffer: MutableAggregationBuffer): Unit = {
    buffer.update(0, Map())
    buffer.update(1, "yearmonthday")
    buffer.update(2, "month")

    val format = new SimpleDateFormat("yyyyMMdd");
    val time = format.format(Calendar.getInstance().getTime()).toInt
    buffer.update(3, time)
    buffer.update(4, time)

  }

  def dayformat(day: String, dimen_mode: String): String = {
    val ca = Calendar.getInstance()
    ca.set(day.substring(0, 4).toInt, day.substring(4, 6).toInt - 1, day.substring(6, 8).toInt)
    dimen_mode match {
      case "y" => ca.get(Calendar.YEAR).toString + "y"
      case "yq" =>
        val m = ca.get(Calendar.MONTH)
        if (m >= 0 && m < 3) {
          ca.get(Calendar.YEAR).toString + "1"
        } else if (m >= 3 && m < 6) {
          ca.get(Calendar.YEAR).toString + "2"
        } else if (m >= 6 && m < 9) {
          ca.get(Calendar.YEAR).toString + "3"
        } else {
          ca.get(Calendar.YEAR).toString + "4"
        }
      case "ym" => ca.get(Calendar.YEAR).toString + "" + (ca.get(Calendar.MONTH) + 1) + ""
      case "yw" => ca.get(Calendar.YEAR).toString + "" + (ca.get(Calendar.WEEK_OF_YEAR)) + ""
      case "ymd" =>
        var m = (ca.get(Calendar.MONTH) + 1).toString
        var d = ca.get(Calendar.DAY_OF_MONTH).toString
        if (m.length < 2) {
          m = "0" + m
        }
        if (d.length < 2) {
          d = "0" + d
        }
        ca.get(Calendar.YEAR) + "" + m + "" + d + ""
      case _ =>
        throw new RuntimeException("nonexistent dimen_mode. [y,yq,ym,yw,ymd]]")
    }
  }


  override def update(buffer: MutableAggregationBuffer, input: Row): Unit = {

    var cat = buffer.getAs[Map[String, Double]](0)

    val datestr = input.getAs[String](0).replaceAll("-", "")
    val reportdate = datestr.toInt
    val min_reportdate = buffer.getAs[Int](3)
    val max_reportdate = buffer.getAs[Int](4)
    if (reportdate < min_reportdate) {
      buffer.update(3, reportdate)
    }
    if (reportdate > max_reportdate) {
      buffer.update(4, reportdate)
    }

    val date2dimen = dayformat(datestr, input.getAs[String](2))
    var value = input.getAs[Double](1)

    val i = cat.getOrElse(date2dimen, 0.00)
    value = i + value
    cat += (date2dimen -> value)

    buffer.update(0, cat)
    buffer.update(1, input.getAs[String](2))
    buffer.update(2, input.getAs[String](3))
  }

  override def merge(buffer1: MutableAggregationBuffer, buffer2: Row): Unit = {

    val cat1 = buffer1.getAs[Map[String, Double]](0)
    val cat2 = buffer2.getAs[Map[String, Double]](0)

    val dog1 = cat1 ++ cat2.map(t => t._1 -> (t._2 + cat1.getOrElse(t._1, 0.00)))
    buffer1.update(0, dog1)

    buffer1.update(1, buffer2.getAs[String](1))
    buffer1.update(2, buffer2.getAs[String](2))

    //
    var min_reportdate1 = buffer1.getAs[Int](3)
    val min_reportdate2 = buffer2.getAs[Int](3)
    if (min_reportdate2 < min_reportdate1) {
      buffer1.update(3, min_reportdate2)
    }

    var max_reportdate1 = buffer1.getAs[Int](4)
    val max_reportdate2 = buffer2.getAs[Int](4)
    if (max_reportdate2 < max_reportdate1) {
      buffer1.update(4, max_reportdate2)
    }

  }

  def getNumFromMatch(input: String): Array[String] = {

    val s = "\\d+"
    val pattern = Pattern.compile(s)
    val ma = pattern.matcher(input)
    var arr: Array[String] = Array()
    while ( {
      ma.find
    }) {
      arr ++ ma.group
    }
    arr
  }

  def creatbasiresult(a: Int, b: Int, dimen_mode: String): Map[String, Double] = {

    var basic_map = Map[String, Double]()
    var flag = true
    var tmp_a = a
    while (flag) {
      val ca = Calendar.getInstance()
      ca.set(tmp_a.toString.substring(0, 4).toInt,
        tmp_a.toString.substring(4, 6).toInt - 1,
        tmp_a.toString.substring(6, 8).toInt)

      ca.add(Calendar.DAY_OF_YEAR, 1)
      var m = (ca.get(Calendar.MONTH) + 1).toString
      var d = ca.get(Calendar.DAY_OF_MONTH).toString
      if (m.length < 2) m = "0" + (m.toInt)
      if (d.length < 2) d = "0" + d

      val rd = (ca.get(Calendar.YEAR) + "" + m + "" + d).toInt
      tmp_a = rd
      basic_map += (dayformat(rd.toString, dimen_mode) -> 0.00)
      if (tmp_a > b) {
        flag = false
      }
    }
    basic_map
  }

  override def evaluate(row: Row): Any = {

    var dog = row.getAs[Map[String, Double]](0)
    val dimen_mode = row.getAs[String](1)
    val time_diff_type = row.getAs[String](2)
    val time_diff: Int = -1
    //        val time_diff = row.getAs[Int](3)
    // creatbasiresult

    var min_reportdate1 = row.getAs[Int](3)
    var max_reportdate1 = row.getAs[Int](4)

    //        println(basic_map)
    //        println("--")
    //        println(dog)
    //        basic_basic_map = creatbasiresult(min_reportdate1, max_reportdate1, dimen_mode)
    //        println(basic_map)
    //        println("--")
    //        println(dog)

    println("--")
    println(dog)
    println("--")
    val result: Map[String, Double] = dimen_mode match {
      case "y" =>
        time_diff_type match {
          case "0" =>
            val dog2 = dog.map(f => {
              val k = f._1
              val v = f._2
              //
              k.substring(0, 4)


              val ca = Calendar.getInstance()
              ca.set(k.substring(0, 4).toInt, 0, 1)
              ca.add(Calendar.YEAR, time_diff)
              val day = getTime(ca)
              //                  println(k + ":::" + day + ":::" +
              //                    dayformat(day, dimen_mode) + ":::" +
              //                    dog.getOrElse(dayformat(day, dimen_mode), 0.00) + ":::" +
              //                    f._2)
              (k, dog.getOrElse(dayformat(day, dimen_mode), 0.00))
            })

            val result_dog = dog.map(m => m._1 -> (m._2 - dog2.getOrElse(m._1, 0.00)))
            result_dog

          case _ => throw new RuntimeException("dimen_mode y must match time_diff_type[0]")
        }
      case "ym" =>
        time_diff_type match {
          case "1" =>
            val dog2 = dog.map(f => {
              val k = f._1
              //

              val ca = Calendar.getInstance()
              ca.set(k.substring(0, 4).toInt, k.substring(4).toInt - 1, 1)
              ca.add(Calendar.YEAR, time_diff)
              val day = getTime(ca)
              (k, dog.getOrElse(dayformat(day, dimen_mode), 0.00))
            })

            val result_dog = dog.map(m => m._1 -> (m._2 - dog2.getOrElse(m._1, 0.00)))
            result_dog

          case "0" =>
            val dog2 = dog.map(f => {
              val k = f._1

              val ca = Calendar.getInstance()
              ca.set(k.substring(0, 4).toInt, k.substring(4).toInt - 1, 1)
              ca.add(Calendar.MONTH, time_diff)
              val day = getTime(ca)
              (k, dog.getOrElse(dayformat(day, dimen_mode), 0.00))
            })
            val result_dog = dog.map(m => m._1 -> (m._2 - dog2.getOrElse(m._1, 0.00)))
            result_dog

          case _ => throw new RuntimeException("dimen_mode ym must match time_diff_type[0,1]")
        }
      case "yq" =>
        time_diff_type match {
          case "1" =>
            val dog2 = dog.map(f => {
              val k = f._1
              val v = f._2
              //

              val ca = Calendar.getInstance()

              val m = k.substring(4).toInt match {
                case 1 => 1
                case 2 => 4
                case 3 => 7
                case _ => 10
              }
              ca.set(k.substring(0, 4).toInt, m - 1, 1)
              ca.add(Calendar.YEAR, time_diff)
              val day = getTime(ca)
              (k, dog.getOrElse(dayformat(day, dimen_mode), 0.00))
            })
            val result_dog = dog.map(m => m._1 -> (m._2 - dog2.getOrElse(m._1, 0.00)))
            result_dog

          case "0" =>
            val dog2 = dog.map(f => {
              val k = f._1
              //

              val ca = Calendar.getInstance()

              val m = k.substring(4).toInt match {
                case 1 => 1
                case 2 => 4
                case 3 => 7
                case _ => 10
              }
              ca.set(k.substring(0, 4).toInt, m - 1, 1)
              ca.add(Calendar.MONTH, time_diff)
              val day = getTime(ca)
              (k, dog.getOrElse(dayformat(day, dimen_mode), 0.00))
            })
            val result_dog = dog.map(m => m._1 -> (m._2 - dog2.getOrElse(m._1, 0.00)))
            result_dog

          case _ => throw new RuntimeException("dimen_mode yq must match time_diff_type[0,1]")
        }

      case "yw" =>
        time_diff_type match {
          case "1" =>
            val dog2 = dog.map(f => {
              val k = f._1
              //

              val ca = Calendar.getInstance()
              ca.setFirstDayOfWeek(Calendar.MONDAY)
              ca.set(Calendar.YEAR, k.substring(0, 4).toInt)
              ca.set(Calendar.WEEK_OF_YEAR, k.substring(4).toInt)
              ca.add(Calendar.YEAR, time_diff)
              val day = getTime(ca)
              (k, dog.getOrElse(dayformat(day, dimen_mode), 0.00))
            })
            val result_dog = dog.map(m => m._1 -> (m._2 - dog2.getOrElse(m._1, 0.00)))
            result_dog

          case "0" =>
            val dog2 = dog.map(f => {
              val k = f._1

              val ca = Calendar.getInstance()
              ca.setFirstDayOfWeek(Calendar.MONDAY)
              ca.set(Calendar.YEAR, k.substring(0, 4).toInt)
              ca.set(Calendar.WEEK_OF_YEAR, k.substring(4).toInt)
              ca.add(Calendar.WEEK_OF_YEAR, time_diff)
              val day = getTime(ca)
              (k, dog.getOrElse(dayformat(day, dimen_mode), 0.00))
            })
            val result_dog = dog.map(m => m._1 -> (m._2 - dog2.getOrElse(m._1, 0.00)))
            result_dog

          case _ => throw new RuntimeException("dimen_mode yw must match time_diff_type[0,1]")
        }

      case "ymd" =>
        time_diff_type match {
          case "3" =>
            val dog2 = dog.map(f => {
              val k = f._1
              //

              val ca = Calendar.getInstance()
              ca.set(k.substring(0, 4).toInt, k.substring(4, 6).toInt - 1, k.substring(6, 8).toInt)
              ca.add(Calendar.YEAR, time_diff)
              val day = getTime(ca)
              println(k + ":::" + day + ":::" +
                dayformat(day, dimen_mode) + ":::" +
                dog.getOrElse(dayformat(day, dimen_mode), 0.00) + ":::" +
                f._2)
              (k, dog.getOrElse(dayformat(day, dimen_mode), 0.00))
            })
            val result_dog = dog.map(m => m._1 -> (m._2 - dog2.getOrElse(m._1, 0.00)))
            result_dog

          case "2" =>
            val dog2 = dog.map(f => {
              val k = f._1

              val ca = Calendar.getInstance()
              ca.set(k.substring(0, 4).toInt, k.substring(4, 6).toInt - 1, k.substring(6, 8).toInt)
              ca.add(Calendar.MONTH, time_diff)
              val day = getTime(ca)
              println(k + ":::" + day + ":::" +
                dayformat(day, dimen_mode) + ":::" +
                dog.getOrElse(dayformat(day, dimen_mode), 0.00) + ":::" +
                f._2)
              (k, dog.getOrElse(dayformat(day, dimen_mode), 0.00))
            })
            val result_dog = dog.map(m => m._1 -> (m._2 - dog2.getOrElse(m._1, 0.00)))
            result_dog

          case "1" =>
            val dog2 = dog.map(f => {
              val k = f._1

              val ca = Calendar.getInstance()
              ca.setFirstDayOfWeek(Calendar.MONDAY)
              ca.set(k.substring(0, 4).toInt, k.substring(4, 6).toInt - 1, k.substring(6, 8).toInt)
              ca.add(Calendar.WEEK_OF_YEAR, time_diff)
              val day = getTime(ca)
              println(k + ":::" + day + ":::" +
                dayformat(day, dimen_mode) + ":::" +
                dog.getOrElse(dayformat(day, dimen_mode), 0.00) + ":::" +
                f._2)
              (k, dog.getOrElse(dayformat(day, dimen_mode), 0.00))
            })
            val result_dog = dog.map(m => m._1 -> (m._2 - dog2.getOrElse(m._1, 0.00)))
            result_dog

          case "0" =>
            val dog2 = dog.map(f => {
              val k = f._1

              val ca = Calendar.getInstance()
              ca.set(k.substring(0, 4).toInt, k.substring(4, 6).toInt - 1, k.substring(6, 8).toInt)
              ca.add(Calendar.DAY_OF_YEAR, time_diff)
              val day = getTime(ca)
              //                  println(k + ":::" + day + ":::" +
              //                    dayformat(day, dimen_mode) + ":::" +
              //                    dog.getOrElse(dayformat(day, dimen_mode), 0.00) + ":::" +
              //                    f._2)
              (k, dog.getOrElse(dayformat(day, dimen_mode), 0.00))
            })
            val result_dog = dog.map(m => m._1 -> (m._2 - dog2.getOrElse(m._1, 0.00)))
            result_dog

          case _ => throw new RuntimeException(
            "dimen_mode ymd must match time_diff_type[0,1,2,3]")
        }

      case _ =>
        throw new RuntimeException("nonexistent dimen_mode. [y,yq,ym,yw,ymd]")
    }

    //    result
    dog
  }
}
