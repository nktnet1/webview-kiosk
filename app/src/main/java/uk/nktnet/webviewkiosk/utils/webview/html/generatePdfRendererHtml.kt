package uk.nktnet.webviewkiosk.utils.webview.html

fun generatePdfRendererHtml(pdfUrl: String): String {
    return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <style>
                body { margin: 0; padding: 0; background-color: #333333; }
                canvas { display: block; margin: 16px auto; max-width: 95%; box-shadow: 0 4px 8px rgba(0,0,0,0.3); }
            </style>
        </head>
        <body>
            <div id="pdf-container"></div>
            <script type="module">
                import * as pdfjsLib from 'https://appassets.androidplatform.net/pdfjs_local/pdf.mjs';
                pdfjsLib.GlobalWorkerOptions.workerSrc = 'https://appassets.androidplatform.net/pdfjs_local/pdf.worker.mjs';

                pdfjsLib.getDocument({ url: '$pdfUrl' }).promise.then(pdf => {
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
                    console.error("PDF.js error: ", err);
                });
            </script>
        </body>
        </html>
    """.trimIndent()
}
