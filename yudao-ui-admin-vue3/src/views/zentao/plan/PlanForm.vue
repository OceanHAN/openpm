<template>
  <Dialog v-model="dialogVisible" :title="dialogTitle" width="680">
    <el-alert
      type="info"
      :closable="false"
      show-icon
      class="mb-15px"
      title="计划的三个禅道特色"
      description="① 分支/平台可以多选（一个计划覆盖多个分支）；② 日期可以「待定」（禅道用哨兵日期 2030-01-01 表示）；③ 子计划的日期必须落在父计划范围内。"
    />
    <el-form ref="formRef" v-loading="formLoading" :model="formData" :rules="formRules" label-width="110px">
      <el-row>
        <el-col :span="12">
          <el-form-item label="所属产品" prop="product">
            <el-select v-model="formData.product" placeholder="请选择产品" filterable class="w-full"
                       :disabled="formType === 'update'" @change="handleProductChange">
              <el-option v-for="p in productList" :key="p.id" :label="p.name" :value="p.id!" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item v-if="branchVisible" :label="branchLabel" prop="branches">
            <el-select v-model="formData.branches" multiple collapse-tags placeholder="请选择" class="w-full">
              <el-option label="主干" :value="0" />
              <el-option v-for="b in branchList" :key="b.id" :label="b.name" :value="b.id!" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item label="计划名称" prop="title">
        <el-input v-model="formData.title" placeholder="如 V1.0 迭代计划" maxlength="90" />
      </el-form-item>

      <el-form-item label="父计划" prop="parent">
        <el-select v-model="formData.parent" placeholder="不选则为独立计划" clearable filterable class="w-full"
                   :disabled="formType === 'update'">
          <el-option v-for="p in parentCandidates" :key="p.id" :label="p.title" :value="p.id!" />
        </el-select>
        <div v-if="formType === 'update'" class="text-12px text-gray-400">
          父计划建好后不支持改挂（禅道也只允许在同产品内调整）
        </div>
      </el-form-item>

      <el-form-item label="计划日期" prop="future">
        <el-switch v-model="formData.future" active-text="待定" inactive-text="指定日期" />
        <span class="ml-12px text-12px text-gray-500">
          待定计划的日期会被写成禅道的哨兵值 {{ FUTURE_DATE }}
        </span>
      </el-form-item>

      <el-row v-if="!formData.future">
        <el-col :span="12">
          <el-form-item label="开始日期" prop="begin">
            <el-date-picker v-model="formData.begin" type="date" value-format="YYYY-MM-DD" class="w-full" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="结束日期" prop="end">
            <el-date-picker v-model="formData.end" type="date" value-format="YYYY-MM-DD" class="w-full" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item label="计划描述" prop="desc">
        <el-input v-model="formData.desc" type="textarea" :rows="3" placeholder="请输入计划描述" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="submitForm" type="primary" :disabled="formLoading">确 定</el-button>
      <el-button @click="dialogVisible = false">取 消</el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import * as PlanApi from '@/api/zentao/plan'
import * as ProductApi from '@/api/zentao/product'
import * as BranchApi from '@/api/zentao/branch'
import { FUTURE_DATE } from './constants'

defineOptions({ name: 'ZentaoPlanForm' })

const message = useMessage()
const { t } = useI18n()
const dialogVisible = ref(false)
const dialogTitle = ref('')
const formLoading = ref(false)
const formType = ref('')
const formRef = ref()
const productList = ref<ProductApi.ProductSimpleVO[]>([])
const branchList = ref<BranchApi.BranchVO[]>([])
const allPlans = ref<PlanApi.PlanVO[]>([])

const formData = ref<PlanApi.PlanVO>({})

// 产品类型决定要不要展示分支，以及叫「分支」还是「平台」
const currentProduct = computed(() => productList.value.find((p) => p.id === formData.value.product))
const branchVisible = computed(
  () => currentProduct.value?.type === 'branch' || currentProduct.value?.type === 'platform'
)
const branchLabel = computed(() => (currentProduct.value?.type === 'platform' ? '平台' : '分支'))

// 父计划候选：同产品、不是自己、且不是自己的子计划（只允许两级）
const parentCandidates = computed(() =>
  allPlans.value.filter((p) => p.id !== formData.value.id && p.parent !== formData.value.id)
)

const formRules = reactive({
  product: [{ required: true, message: '所属产品不能为空', trigger: 'change' }],
  title: [{ required: true, message: '计划名称不能为空', trigger: 'blur' }],
  begin: [
    {
      validator: (_r: any, _v: any, cb: any) =>
        formData.value.future || formData.value.begin ? cb() : cb(new Error('开始日期不能为空')),
      trigger: 'change'
    }
  ],
  end: [
    {
      validator: (_r: any, _v: any, cb: any) =>
        formData.value.future || formData.value.end ? cb() : cb(new Error('结束日期不能为空')),
      trigger: 'change'
    }
  ]
})

const handleProductChange = async () => {
  formData.value.branches = branchVisible.value ? [] : [0]
  formData.value.parent = undefined
  await loadBranchAndPlans()
}

const loadBranchAndPlans = async () => {
  if (!formData.value.product) {
    branchList.value = []
    allPlans.value = []
    return
  }
  if (branchVisible.value) {
    branchList.value = await BranchApi.getBranchListByProduct(formData.value.product)
  } else {
    branchList.value = []
  }
  const page = await PlanApi.getPlanPage({ pageNo: 1, pageSize: 100, product: formData.value.product })
  allPlans.value = page.list
}

const open = async (type: string, id?: number, productId?: number) => {
  dialogVisible.value = true
  formType.value = type
  dialogTitle.value = type === 'create' ? '新建计划' : '编辑计划'
  resetForm()
  productList.value = await ProductApi.getProductSimpleList()
  if (id) {
    formLoading.value = true
    try {
      const data = await PlanApi.getPlan(id)
      formData.value = {
        ...data,
        // 后端返回的是逗号串，表单用数组
        branches: (data.branch || '0').split(',').map((x) => Number(x.trim()))
      }
      await loadBranchAndPlans()
    } finally {
      formLoading.value = false
    }
  } else if (productId) {
    formData.value.product = productId
    await loadBranchAndPlans()
  }
}
defineExpose({ open })

const emit = defineEmits(['success'])
const submitForm = async () => {
  if (!formRef.value) return
  await formRef.value.validate()
  formLoading.value = true
  try {
    const payload = { ...formData.value }
    if (payload.future) {
      payload.begin = undefined
      payload.end = undefined
    }
    if (formType.value === 'create') {
      await PlanApi.createPlan(payload)
      message.success(t('common.createSuccess'))
    } else {
      await PlanApi.updatePlan(payload)
      message.success(t('common.updateSuccess'))
    }
    dialogVisible.value = false
    emit('success')
  } finally {
    formLoading.value = false
  }
}

const resetForm = () => {
  formData.value = { product: undefined, branches: [0], title: '', desc: '', future: false, parent: undefined }
  branchList.value = []
  allPlans.value = []
  formRef.value?.resetFields()
}
</script>
