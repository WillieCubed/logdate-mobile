package app.logdate.feature.editor.ui.handwriting

import app.logdate.feature.postcards.model.CanvasElement
import app.logdate.feature.postcards.model.ElementTransform
import app.logdate.feature.postcards.model.InkPoint
import app.logdate.feature.postcards.model.InkTool
import app.logdate.shared.model.HandwritingDocument
import app.logdate.shared.model.HandwritingPoint
import app.logdate.shared.model.HandwritingStroke

fun HandwritingPage.toDocument(): HandwritingDocument =
    HandwritingDocument(
        width = width,
        height = height,
        strokes =
            strokes.map { stroke ->
                HandwritingStroke(
                    id = stroke.id,
                    tool = stroke.tool.name.lowercase(),
                    color = stroke.color,
                    width = stroke.strokeWidth,
                    points = stroke.points.map { HandwritingPoint(it.x, it.y, it.pressure) },
                    x = stroke.transform.x,
                    y = stroke.transform.y,
                    rotation = stroke.transform.rotation,
                    scaleX = stroke.transform.scaleX,
                    scaleY = stroke.transform.scaleY,
                    zIndex = stroke.zIndex,
                )
            },
    )

fun HandwritingDocument.toPage(): HandwritingPage {
    require(version == 1) { "Unsupported handwriting document version: $version" }
    return HandwritingPage(
        width = width,
        height = height,
        strokes =
            strokes.map { stroke ->
                CanvasElement.Ink(
                    id = stroke.id,
                    tool = InkTool.valueOf(stroke.tool.uppercase()),
                    color = stroke.color,
                    strokeWidth = stroke.width,
                    points = stroke.points.map { InkPoint(it.x, it.y, it.pressure) },
                    transform = ElementTransform(stroke.x, stroke.y, stroke.rotation, stroke.scaleX, stroke.scaleY),
                    zIndex = stroke.zIndex,
                )
            },
    )
}
