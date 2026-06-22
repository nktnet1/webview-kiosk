package uk.nktnet.webviewkiosk.utils.webview.html

import uk.nktnet.webviewkiosk.config.Constants

fun generatePdfRendererHtml(pdfBase64Data: String): String {
    return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <style>
                body {
                    margin: 0;
                    padding: 0;
                    background-color: #333333;
                }
                canvas {
                    display: block;
                    margin: 16px auto;
                    max-width: 95%;
                    box-shadow: 0 4px 8px rgba(0, 0, 0, 0.3);
                }
            </style>
        </head>
        <body>
            <div id="pdf-container"></div>
            <script type="module">
                import * as pdfjsLib from '${Constants.PDF_JS_ASSETS_DUMMY_URL}/pdfjs_local/pdf.mjs';
                pdfjsLib.GlobalWorkerOptions.workerSrc = '${Constants.PDF_JS_ASSETS_DUMMY_URL}/pdfjs_local/pdf.worker.mjs';

                try {
                    // Decode the injected Base64 data bundle directly into a byte array
                    const base64Data = "$pdfBase64Data";
                    const binaryString = atob(base64Data);
                    const len = binaryString.length;
                    const bytes = new Uint8Array(len);
                    for (let i = 0; i < len; i++) {
                        bytes[i] = binaryString.charCodeAt(i);
                    }

                    // Feed raw binary array to PDF.js
                    pdfjsLib.getDocument({ data: bytes }).promise.then(pdf => {
                        const container = document.getElementById('pdf-container');
                        container.innerHTML = '';
                        for (let i = 1; i <= pdf.numPages; i++) {
                            pdf.getPage(i).then(page => {
                                const viewport = page.getViewport({ scale: 1.5 });
                                const canvas = document.createElement('canvas');
                                const context = canvas.getContext('2d');
                                canvas.height = viewport.height;
                                canvas.width = viewport.width;
                                container.appendChild(canvas);
                                page.render({ canvasContext: context, viewport: viewport });
                            });
                        }
                    }).catch(err => {
                        console.error("PDF.js inner error: ", err);
                    });
                } catch (err) {
                    console.error("Base64 decoding error: ", err);
                }
            </script>
        </body>
        </html>
    """.trimIndent()
}