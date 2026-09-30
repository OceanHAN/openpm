<template>
  <ContentWrap>
    <el-alert
      type="info"
      :closable="false"
      show-icon
      class="mb-15px"
      title="测试仪表盘：禅道的 qa 模块只有 153 行，它本身不算数据"
      description="禅道 qa.index 只是把「块（block）」拼成看板，看板里那块质量统计又调 metric 的度量口径。本实现不搬 block 的积木引擎、也不依赖 metric 框架，直接把那四块内容按同样的口径用 SQL 算出来 —— 有价值的是口径，不是积木框架。"
    />

    <el-form class="-mb-15px" :inline="true" label-width="70px">
      <el-form-item label="产品">
        <el-select v-model="query.product" clearable filterable placeholder="全部产品" class="!w-220px"
                   @change="load">
          <el-option v-for="p in products" :key="p.id" :label="p.name" :value="p.id!" />
        </el-select>
      </el-form-item>
      <el-form-item label="区间">
        <el-select v-model="query.days" class="!w-140px" @change="load">
          <el-option label="最近 7 天" :value="7" />
          <el-option label="最近 14 天" :value="14" />
          <el-option label="最近 30 天" :value="30" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="load"><Icon icon="ep:refresh" class="mr-5px" /> 刷新</el-button>
        <span class="ml-8px text-12px text-gray-500">统计区间：{{ data.begin }} ~ 今天</span>
      </el-form-item>
    </el-form>

    <el-row :gutter="15" class="mb-15px mt-15px">
      <el-col :span="4">
        <el-card shadow="never" class="text-center">
          <div class="text-24px font-bold text-primary">{{ data.summary?.bugTotal ?? 0 }}</div>
          <div class="text-12px text-gray-500">缺陷总数</div>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="never" class="text-center">
          <div class="text-24px font-bold text-red-500">{{ data.summary?.bugActive ?? 0 }}</div>
          <div class="text-12px text-gray-500">待处理（激活）</div>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="never" class="text-center">
          <div class="text-24px font-bold text-orange-500">{{ data.summary?.bugEffective ?? 0 }}</div>
          <div class="text-12px text-gray-500">有效缺陷</div>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="never" class="text-center">
          <div class="text-24px font-bold text-green-600">{{ data.summary?.bugFixRate ?? 0 }}%</div>
          <div class="text-12px text-gray-500">
            修复率（{{ data.summary?.bugFixed ?? 0 }}/{{ data.summary?.bugEffective ?? 0 }}）
          </div>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="never" class="text-center">
          <div class="text-24px font-bold">{{ data.summary?.caseWait ?? 0 }}/{{ data.summary?.caseTotal ?? 0 }}</div>
          <div class="text-12px text-gray-500">待评审 / 用例总数</div>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="never" class="text-center">
          <div class="text-24px font-bold text-purple-500">
            {{ data.summary?.testTaskUnclosed ?? 0 }}/{{ data.summary?.testTaskTotal ?? 0 }}
          </div>
          <div class="text-12px text-gray-500">未完成 / 测试单</div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="mb-15px">
      <template #header>
        <span class="font-bold">按产品的质量统计</span>
        <span class="ml-8px text-12px text-gray-400">
          修复率 = 已修复 ÷ 有效缺陷；有效缺陷 = 状态激活 或 解决方案为已修复/延期处理/不予解决
        </span>
      </template>
      <el-table :data="data.productQuality || []" size="small">
        <el-table-column label="产品" prop="productName" min-width="160" show-overflow-tooltip />
        <el-table-column label="新增" prop="opened" width="70" align="center" />
        <el-table-column label="解决" prop="resolved" width="70" align="center" />
        <el-table-column label="关闭" prop="closed" width="70" align="center" />
        <el-table-column label="激活" prop="active" width="70" align="center" />
        <el-table-column label="有效缺陷" prop="effective" width="90" align="center" />
        <el-table-column label="已修复" prop="fixed" width="80" align="center" />
        <el-table-column label="修复率" min-width="140">
          <template #default="{ row }">
            <el-progress :percentage="fixRateOf(row)" :stroke-width="10" />
          </template>
        </el-table-column>
        <el-table-column label="未完成测试单" prop="unclosedTestTasks" width="110" align="center" />
        <el-table-column label="待评审用例" prop="reviewCases" width="100" align="center" />
      </el-table>
    </el-card>

    <el-row :gutter="15" class="mb-15px">
      <el-col :span="8">
        <el-card shadow="never">
          <template #header><span class="font-bold">缺陷状态</span></template>
          <el-tag v-for="item in data.bugStatus || []" :key="item.name" class="mr-5px mb-5px">
            {{ bugStatusLabel(item.name) }} {{ item.value }}
          </el-tag>
          <el-empty v-if="!(data.bugStatus || []).length" description="没有缺陷" :image-size="60" />
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never">
          <template #header><span class="font-bold">严重程度</span></template>
          <el-tag v-for="item in data.bugSeverity || []" :key="item.name" type="danger" class="mr-5px mb-5px">
            {{ item.name }} 级 {{ item.value }}
          </el-tag>
          <el-empty v-if="!(data.bugSeverity || []).length" description="没有缺陷" :image-size="60" />
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never">
          <template #header><span class="font-bold">解决方案 / 用例结果</span></template>
          <el-tag v-for="item in data.bugResolution || []" :key="item.name" type="success" class="mr-5px mb-5px">
            {{ resolutionLabel(item.name) }} {{ item.value }}
          </el-tag>
          <el-tag v-for="item in data.caseResult || []" :key="item.name" type="warning" class="mr-5px mb-5px">
            用例{{ caseResultLabel(item.name) }} {{ item.value }}
          </el-tag>
          <el-empty v-if="!(data.bugResolution || []).length && !(data.caseResult || []).length"
                    description="还没有解决记录与用例执行结果" :image-size="60" />
        </el-card>
      </el-col>
    </el-row>

    <el-tabs v-model="activeTab">
      <el-tab-pane :label="`待处理缺陷（${(data.pendingBugs || []).length}）`" name="bug">
        <el-table :data="data.pendingBugs || []" size="small" empty-text="没有待处理的缺陷">
          <el-table-column label="编号" prop="id" width="80" align="center" />
          <el-table-column label="缺陷标题" prop="title" min-width="240" show-overflow-tooltip />
          <el-table-column label="产品" prop="product" width="80" align="center" />
          <el-table-column label="严重" prop="severity" width="70" align="center" />
          <el-table-column label="优先级" prop="pri" width="80" align="center" />
          <el-table-column label="指派给" prop="assignedTo" width="100" align="center" />
          <el-table-column label="截止" prop="deadline" width="110" align="center" />
        </el-table>
      </el-tab-pane>
      <el-tab-pane :label="`待评审用例（${(data.reviewCases || []).length}）`" name="case">
        <el-table :data="data.reviewCases || []" size="small" empty-text="没有待评审的用例">
          <el-table-column label="编号" prop="id" width="80" align="center" />
          <el-table-column label="用例标题" prop="title" min-width="240" show-overflow-tooltip />
          <el-table-column label="产品" prop="product" width="80" align="center" />
          <el-table-column label="类型" prop="type" width="110" align="center" />
          <el-table-column label="阶段" prop="stage" width="110" align="center" />
          <el-table-column label="创建人" prop="openedBy" width="100" align="center" />
        </el-table>
      </el-tab-pane>
      <el-tab-pane :label="`未完成测试单（${(data.unclosedTestTasks || []).length}）`" name="testtask">
        <el-table :data="data.unclosedTestTasks || []" size="small" empty-text="没有未完成的测试单">
          <el-table-column label="编号" prop="id" width="80" align="center" />
          <el-table-column label="测试单名称" prop="name" min-width="220" show-overflow-tooltip />
          <el-table-column label="产品" prop="product" width="80" align="center" />
          <el-table-column label="状态" prop="status" width="100" align="center" />
          <el-table-column label="负责人" prop="owner" width="100" align="center" />
          <el-table-column label="起止" width="200" align="center">
            <template #default="{ row }">{{ row.begin }} ~ {{ row.end }}</template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>
  </ContentWrap>
</template>

<script lang="ts" setup>
import * as QaApi from '@/api/zentao/qa'
import * as ProductApi from '@/api/zentao/product'

defineOptions({ name: 'ZentaoQa' })

const query = reactive<{ product?: number; days: number }>({ days: 7 })
const data = ref<Partial<QaApi.QaDashboardVO>>({})
const products = ref<ProductApi.ProductVO[]>([])
const activeTab = ref('bug')

const BUG_STATUS: Record<string, string> = {
  active: '激活', resolved: '已解决', closed: '已关闭'
}
const RESOLUTION: Record<string, string> = {
  fixed: '已修复', duplicate: '重复', postponed: '延期处理', willnotfix: '不予解决',
  notrepro: '无法重现', bydesign: '设计如此', external: '外部原因', tocancel: '不予解决'
}
const CASE_RESULT: Record<string, string> = {
  pass: '通过', fail: '失败', blocked: '阻塞', investigate: '需调查'
}
const bugStatusLabel = (v: string) => BUG_STATUS[v] || v
const resolutionLabel = (v: string) => RESOLUTION[v] || v
const caseResultLabel = (v: string) => CASE_RESULT[v] || v

// 修复率：分母是「有效缺陷」而不是缺陷总数（与后端 summary 同一口径）
const fixRateOf = (row: any) => {
  const effective = Number(row.effective || 0)
  const fixed = Number(row.fixed || 0)
  return effective === 0 ? 0 : Math.round((fixed / effective) * 100)
}

const load = async () => {
  data.value = await QaApi.getQaDashboard({ product: query.product, days: query.days })
}

onMounted(async () => {
  const page = await ProductApi.getProductPage({ pageNo: 1, pageSize: 100 })
  products.value = page.list || []
  await load()
})
</script>
