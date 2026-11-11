import { initSearch } from './modules/search.js';
import { initSettings } from './modules/settings.js';
import { initKeybindings } from './modules/keybindings.js';
import { initRouter } from './modules/router.js';
import { loadFileTree } from './modules/file-tree.js';
import { initInspector } from './modules/inspector.js';
import { initUserData } from './modules/user-data.js';
import { initFileViewer } from './modules/file-viewer.js';
import { initQuickOpen, openQuickOpen } from './modules/quick-open.js';
import { copyShareableLink } from './modules/router.js';
import { initBranchSelector } from './modules/branch-selector.js';
import { initNavHistory } from './modules/nav-history.js';
import { initDivider } from './modules/divider.js';
import { initContextMenu } from './modules/context-menu.js';

// Startup: wire up all subsystems
initSearch();
initSettings();
initKeybindings();
initInspector();
initRouter();
loadFileTree();
initUserData();
initFileViewer();
initBranchSelector();
initQuickOpen();
initDivider();
initContextMenu();
initNavHistory();

// Expose for keybindings module
window._openQuickOpen = openQuickOpen;

// Copy Link button
document.getElementById('copy-link-btn').addEventListener('click', copyShareableLink);
