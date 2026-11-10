import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App';
import { registerJspLanguage } from './languages/jsp';

registerJspLanguage();

ReactDOM.createRoot(document.getElementById('root')).render(<App />);