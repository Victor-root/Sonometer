package com.example.djmeter.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import com.itextpdf.io.font.constants.StandardFonts
import com.itextpdf.kernel.font.PdfFont
import com.itextpdf.kernel.font.PdfFontFactory
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Image
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.io.image.ImageDataFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow
import com.itextpdf.layout.element.Table
import com.itextpdf.layout.element.Cell
import com.itextpdf.kernel.colors.ColorConstants
import com.itextpdf.layout.properties.HorizontalAlignment
import com.itextpdf.layout.properties.TextAlignment

class PdfGenerator {
    fun generateDecibelGraphPdf(context: Context, readings: List<Float>): String {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "decibel_graph_$timeStamp.pdf"
        val filePath = File(context.getExternalFilesDir(null), fileName)

        // Create PDF document
        val writer = PdfWriter(FileOutputStream(filePath))
        val pdf = PdfDocument(writer)
        val document = Document(pdf)
        val bold = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD)

        try {
            // Add title and header
            document.add(Paragraph("Decibel Readings Report")
                .setFontSize(24f)
                .setFont(bold)
                .setTextAlignment(TextAlignment.CENTER))
            
            document.add(Paragraph("Generated on: ${
                SimpleDateFormat("dd MMMM yyyy, HH:mm:ss", Locale.getDefault()).format(Date())
            }").setTextAlignment(TextAlignment.CENTER))
            
            document.add(Paragraph("\n"))
            
            // Create and add graph bitmap
            val graphBitmap = createGraphBitmap(readings)
            val stream = ByteArrayOutputStream()
            graphBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            val imageData = ImageDataFactory.create(stream.toByteArray())
            val image = Image(imageData).setHorizontalAlignment(HorizontalAlignment.CENTER)
            document.add(image)
            
            // Add analysis section
            document.add(Paragraph("\nSound Level Analysis")
                .setFontSize(18f)
                .setFont(bold))

            // Calculate statistics
            // Energy average (Leq) of the levels, not their arithmetic mean.
            val avgDecibel = 10 * log10(readings.map { 10.0.pow(it / 10.0) }.average())
            val maxDecibel = readings.maxOrNull() ?: 0f
            val minDecibel = readings.minOrNull() ?: 0f
            
            // Create statistics table
            val table = Table(2).useAllAvailableWidth()
            
            // Add rows to table
            addTableRow(table, bold, "Average Sound Level", "${String.format("%.1f", avgDecibel)} dB")
            addTableRow(table, bold, "Maximum Sound Level", "${String.format("%.1f", maxDecibel)} dB")
            addTableRow(table, bold, "Minimum Sound Level", "${String.format("%.1f", minDecibel)} dB")
            addTableRow(table, bold, "Duration", "${readings.size / 10} seconds")
            
            // Sound level classification
            val classification = when {
                avgDecibel < 50 -> "Low (Safe Level)"
                avgDecibel < 70 -> "Moderate (Normal Level)"
                avgDecibel < 85 -> "High (Potentially Harmful)"
                else -> "Very High (Dangerous Level)"
            }
            addTableRow(table, bold, "Sound Classification", classification)
            
            // Environment assessment
            val environment = when {
                avgDecibel < 40 -> "Quiet Environment (Library-like)"
                avgDecibel < 60 -> "Normal Indoor Environment"
                avgDecibel < 80 -> "Busy Urban Environment"
                else -> "Industrial or Very Noisy Environment"
            }
            addTableRow(table, bold, "Environment Assessment", environment)
            
            document.add(table)
            
            // Add recommendations section
            document.add(Paragraph("\nRecommendations")
                .setFontSize(18f)
                .setFont(bold))
            
            val recommendations = when {
                avgDecibel > 85 -> listOf(
                    "• Hearing protection is strongly recommended",
                    "• Limit exposure time",
                    "• Consider noise reduction measures"
                )
                avgDecibel > 70 -> listOf(
                    "• Monitor exposure duration",
                    "• Take regular breaks from this environment",
                    "• Be aware of potential hearing fatigue"
                )
                else -> listOf(
                    "• Sound levels are within safe limits",
                    "• Continue monitoring for any significant changes",
                    "• Maintain current acoustic environment"
                )
            }
            
            recommendations.forEach { rec ->
                document.add(Paragraph(rec))
            }
            
            // Add detailed readings in a collapsible section
            document.add(Paragraph("\nDetailed Readings")
                .setFontSize(18f)
                .setFont(bold))
            
            // Create readings table
            val readingsTable = Table(3).useAllAvailableWidth()
            addTableHeader(readingsTable, bold, "Time", "Reading (dB)", "Level")
            
            readings.forEachIndexed { index, value ->
                val timeStamp = index * 100 // 100ms intervals
                val level = when {
                    value < 50 -> "Low"
                    value < 70 -> "Moderate"
                    value < 85 -> "High"
                    else -> "Very High"
                }
                addReadingRow(readingsTable, 
                    "${timeStamp/1000}.${timeStamp%1000}s", 
                    String.format("%.1f", value), 
                    level
                )
            }
            
            document.add(readingsTable)
            
        } finally {
            document.close()
        }

        return filePath.absolutePath
    }

    private fun addTableRow(table: Table, bold: PdfFont, label: String, value: String) {
        table.addCell(Cell().add(Paragraph(label).setFont(bold)))
        table.addCell(Cell().add(Paragraph(value)))
    }

    private fun addTableHeader(table: Table, bold: PdfFont, vararg headers: String) {
        headers.forEach { header ->
            table.addHeaderCell(Cell()
                .setBackgroundColor(ColorConstants.LIGHT_GRAY)
                .add(Paragraph(header).setFont(bold)))
        }
    }

    private fun addReadingRow(table: Table, time: String, value: String, level: String) {
        table.addCell(Cell().add(Paragraph(time)))
        table.addCell(Cell().add(Paragraph(value)))
        table.addCell(Cell().add(Paragraph(level)))
    }

    private fun createGraphBitmap(readings: List<Float>): Bitmap {
        val width = 800
        val height = 400
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        // Fill background
        val backgroundPaint = Paint().apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
        
        // Draw grid lines
        val gridPaint = Paint().apply {
            color = android.graphics.Color.LTGRAY
            strokeWidth = 1f
            style = Paint.Style.STROKE
        }
        
        // Horizontal grid lines
        for (i in 0..4) {
            val y = height * i / 4f
            canvas.drawLine(0f, y, width.toFloat(), y, gridPaint)
        }
        
        // Vertical grid lines
        for (i in 0..8) {
            val x = width * i / 8f
            canvas.drawLine(x, 0f, x, height.toFloat(), gridPaint)
        }
        
        // Draw graph line
        val linePaint = Paint().apply {
            color = android.graphics.Color.rgb(29, 92, 219) // Your app's blue color
            strokeWidth = 3f
            style = Paint.Style.STROKE
            isAntiAlias = true
        }

        val path = Path()
        val points = readings.size
        val dx = width.toFloat() / (points - 1)
        
        path.moveTo(0f, height - (height * (readings[0] / 120f)))
        
        for (i in 1 until points) {
            val x = i * dx
            val y = height - (height * (readings[i] / 120f))
            path.lineTo(x, y)
        }

        canvas.drawPath(path, linePaint)
        return bitmap
    }
} 