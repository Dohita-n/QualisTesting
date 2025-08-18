const axios = require('axios');

async function testDatasetRows() {
  try {
    // You'll need to replace these with actual values from your application
    const datasetId = 'your-dataset-id';
    const userId = 'your-user-id';
    const token = 'your-auth-token';
    
    const response = await axios.get(`http://localhost:8080/api/datasets/${datasetId}/rows`, {
      params: {
        userId: userId,
        page: 0,
        size: 200
      },
      headers: {
        'Authorization': `Bearer ${token}`
      }
    });
    
    console.log('API Response:', JSON.stringify(response.data, null, 2));
    
    // Check the first few rows
    const content = response.data.content;
    console.log('\nFirst 5 rows:');
    content.slice(0, 5).forEach((row, index) => {
      console.log(`Row ${index}: ID=${row.id}, rowNumber=${row.rowNumber}, data keys=${Object.keys(row.data)}`);
    });
    
  } catch (error) {
    console.error('Error:', error.response?.data || error.message);
  }
}

testDatasetRows(); 