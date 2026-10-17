/*******************************************************************************

    uBlock Origin - a browser extension to block requests.
    Copyright (C) 2019-present Raymond Hill

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see {http://www.gnu.org/licenses/}.

    Home: https://github.com/gorhill/uBlock
*/

(function () {
  self.adsbygoogle = self.adsbygoogle || {
    loaded: true,
    push: function () {},
  };
  let adCount = 1;
  const setupAd = (placeholder) => {
    const fr = document.createElement("iframe");
    fr.id = `aswift_${adCount}`;
    fr.setAttribute("name", fr.id);
    adCount += 1;
    placeholder.dataset.adsbygoogleStatus = "loading";
    placeholder.dataset.adStatus = "loading";
    placeholder.appendChild(fr);
    fr.addEventListener(
      "load",
      () => {
        placeholder.dataset.adsbygoogleStatus = "done";
        placeholder.dataset.adStatus = "filled";
        fr.dataset.loadComplete = "true";
      },
      { once: true },
    );
    fr.contentWindow.location =
      "data:text/html;charset=utf-8;base64,PCFET0NUWVBFIGh0bWw+DQo8aHRtbD4NCiAgICA" +
      "8aGVhZD48dGl0bGU+PC90aXRsZT48L2hlYWQ+DQogICAgPGJvZHk+PC9ib2R5Pg0KPC9odG1sPg" +
      "==";
  };
  // Webview Kiosk: visit added/changed subtrees in bounded batches. Keep native
  // animation frames/timers supplied by the scriptlet factory.
  const pending = [];
  let scheduled = false;
  let stopped = false;
  function queue(root) {
    if (!root || ![1, 9].includes(root.nodeType) || stopped) return;
    if (pending.some((item) => item.root.contains(root))) return;
    pending.push({ root, walker: null });
    if (!scheduled) {
      scheduled = true;
      requestAnimationFrame(process);
    }
  }
  function process() {
    scheduled = false;
    if (stopped) return;
    let budget = 400;
    const inspect = (node) => {
      if (
        node.nodeType === 1 &&
        node.matches("ins.adsbygoogle:not([data-ad-status][data-adsbygoogle-status])")
      )
        setupAd(node);
    };
    while (pending.length && budget > 0) {
      const item = pending[0];
      if (!item.walker) {
        inspect(item.root);
        item.walker = document.createTreeWalker(item.root, 1);
        budget--;
      }
      let node;
      while (budget > 0 && (node = item.walker.nextNode())) {
        inspect(node);
        budget--;
      }
      if (budget > 0) pending.shift();
    }
    if (pending.length) {
      scheduled = true;
      requestAnimationFrame(process);
    }
  }
  const observer = new MutationObserver((records) => {
    for (const record of records) {
      if (record.type === "attributes") queue(record.target);
      else record.addedNodes.forEach(queue);
    }
  });
  observer.observe(document, {
    attributes: true,
    attributeFilter: ["class"],
    childList: true,
    subtree: true,
  });
  queue(document);
  setTimeout(() => {
    stopped = true;
    observer.disconnect();
    pending.length = 0;
  }, 20000);
})();
