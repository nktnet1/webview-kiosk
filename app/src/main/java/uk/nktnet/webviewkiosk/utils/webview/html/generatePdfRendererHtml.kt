package uk.nktnet.webviewkiosk.utils.webview.html

import androidx.core.net.toUri
import org.json.JSONObject
import uk.nktnet.webviewkiosk.config.Constants

fun generatePdfRendererHtml(pdfSourceToken: String): String {
    val pdfSourceUrl = Constants.PDF_JS_ASSETS_DUMMY_URL.toUri()
        .buildUpon()
        .appendPath("pdf_source")
        .appendQueryParameter("wk_pdf_token", pdfSourceToken)
        .build()
        .toString()
    val quotedPdfSourceUrl = JSONObject.quote(pdfSourceUrl)
    val pdfStatusUrl = Constants.PDF_JS_ASSETS_DUMMY_URL.toUri()
        .buildUpon()
        .appendPath("pdf_status")
        .appendQueryParameter("wk_pdf_token", pdfSourceToken)
        .build()
        .toString()
    val quotedPdfStatusUrl = JSONObject.quote(pdfStatusUrl)

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
                .page-slot {
                    width: 95%;
                    min-height: 70vh;
                    margin: 16px auto;
                }
                canvas {
                    display: block;
                    width: 100%;
                    height: auto;
                    box-shadow: 0 4px 8px rgba(0, 0, 0, 0.3);
                }
                #pdf-status {
                    padding: 24px;
                    color: #ffffff;
                    text-align: center;
                    font-family: sans-serif;
                }
                #pdf-retry {
                    padding: 10px 24px;
                    font-size: 16px;
                }
            </style>
        </head>
        <body>
            <div id="pdf-status" role="status" aria-live="polite">
                <p id="pdf-message">Loading PDF…</p>
                <button id="pdf-retry" type="button">Retry</button>
            </div>
            <div id="pdf-container"></div>
            <script>
                window.WKPdfViewer = (function() {
                    var status = document.getElementById('pdf-status');
                    var message = document.getElementById('pdf-message');
                    var loaded = false;
                    var pageFailed = false;
                    var reports = Promise.resolve();

                    function report(event) {
                        // Keep native status updates in order when rendering fails soon after loading.
                        reports = reports.then(function() {
                            return fetch($quotedPdfStatusUrl + '&event=' + event, { cache: 'no-store' });
                        })
                            .catch(function(err) { console.error('PDF status error:', err); });
                    }

                    function showError(err) {
                        message.textContent = 'Unable to load this PDF. Check your connection or sign in, then try again.';
                        status.hidden = false;
                        report(err && err.status === 401 ? 'authentication' : 'error');
                    }

                    function failed(err) {
                        if (!loaded) showError(err);
                    }

                    document.getElementById('pdf-retry').onclick = function() {
                        report('retry');
                    };
                    window.addEventListener('error', function(event) {
                        if (event.target === window || event.target.tagName === 'SCRIPT') {
                            failed();
                        }
                    }, true);

                    return {
                        failed: failed,
                        pageFailed: function(err) {
                            pageFailed = true;
                            showError(err);
                        },
                        loaded: function() {
                            loaded = true;
                            if (pageFailed) return;
                            status.hidden = true;
                            report('loaded');
                        }
                    };
                })();
            </script>
            <script type="module">
                import * as pdfjsLib from '${Constants.PDF_JS_ASSETS_DUMMY_URL}/pdfjs_local/pdf.mjs';
                pdfjsLib.GlobalWorkerOptions.workerSrc = '${Constants.PDF_JS_ASSETS_DUMMY_URL}/pdfjs_local/pdf.worker.mjs';

                const pdfSourceUrl = $quotedPdfSourceUrl;
                const MAX_CONCURRENT_RENDERS = 2;
                const renderQueue = [];
                let activeRenders = 0;

                function pumpQueue() {
                    while (activeRenders < MAX_CONCURRENT_RENDERS && renderQueue.length > 0) {
                        const state = renderQueue.shift();
                        state.queued = false;

                        if (!state.wanted || state.rendered || state.rendering) {
                            continue;
                        }

                        activeRenders++;
                        renderPage(state).finally(function() {
                            activeRenders--;
                            pumpQueue();
                        });
                    }
                }

                function queueRender(state) {
                    state.wanted = true;
                    if (state.rendered || state.rendering || state.queued) {
                        return;
                    }
                    state.queued = true;
                    renderQueue.push(state);
                    pumpQueue();
                }

                function unloadPage(state) {
                    state.wanted = false;
                    state.generation++;
                    if (state.renderTask) {
                        try {
                            state.renderTask.cancel();
                        } catch (_) {}
                    }
                    if (state.canvas) {
                        state.canvas.remove();
                        state.canvas = null;
                    }
                    state.renderTask = null;
                    state.rendered = false;
                }

                async function renderPage(state) {
                    state.rendering = true;
                    const generation = state.generation;
                    let page = null;

                    try {
                        page = await state.pdf.getPage(state.pageNumber);
                        if (!state.wanted || generation !== state.generation) {
                            return;
                        }

                        const viewport = page.getViewport({ scale: 1.5 });
                        state.slot.style.minHeight = '0';
                        state.slot.style.aspectRatio = viewport.width + ' / ' + viewport.height;

                        const canvas = document.createElement('canvas');
                        const context = canvas.getContext('2d');
                        canvas.height = Math.ceil(viewport.height);
                        canvas.width = Math.ceil(viewport.width);
                        state.canvas = canvas;
                        while (state.slot.firstChild) {
                            state.slot.removeChild(state.slot.firstChild);
                        }
                        state.slot.appendChild(canvas);

                        state.renderTask = page.render({
                            canvasContext: context,
                            viewport: viewport
                        });
                        await state.renderTask.promise;

                        if (!state.wanted || generation !== state.generation || state.canvas !== canvas) {
                            return;
                        }

                        state.rendered = true;
                    } catch (err) {
                        if (!err || err.name !== 'RenderingCancelledException') {
                            console.error('PDF.js page render error:', err);
                            if (state.wanted && generation === state.generation) {
                                window.WKPdfViewer.pageFailed(err);
                            }
                        }
                    } finally {
                        state.renderTask = null;
                        state.rendering = false;
                        if (page) {
                            try {
                                page.cleanup();
                            } catch (_) {}
                        }
                        // An observer batch can re-enter while cancellation is still settling.
                        // Retry that obsolete render, but do not loop on ordinary PDF errors.
                        if (state.wanted && generation !== state.generation) {
                            queueRender(state);
                        }
                    }
                }

                pdfjsLib.getDocument({
                    url: pdfSourceUrl
                }).promise.then(pdf => {
                    const container = document.getElementById('pdf-container');
                    container.innerHTML = '';

                    const observer = new IntersectionObserver(function(entries) {
                        entries.forEach(function(entry) {
                            const state = entry.target.__pdfPageState;
                            if (!state) return;

                            if (entry.isIntersecting) {
                                queueRender(state);
                            } else {
                                unloadPage(state);
                            }
                        });
                    }, {
                        rootMargin: '150% 0px'
                    });

                    for (let i = 1; i <= pdf.numPages; i++) {
                        const slot = document.createElement('div');
                        slot.className = 'page-slot';
                        slot.__pdfPageState = {
                            pdf: pdf,
                            pageNumber: i,
                            slot: slot,
                            wanted: false,
                            queued: false,
                            rendering: false,
                            rendered: false,
                            generation: 0,
                            renderTask: null,
                            canvas: null
                        };
                        container.appendChild(slot);
                        observer.observe(slot);
                    }
                    window.WKPdfViewer.loaded();
                }).catch(err => {
                    console.error('PDF.js error:', err);
                    window.WKPdfViewer.failed(err);
                });
            </script>
        </body>
        </html>
    """.trimIndent()
}
